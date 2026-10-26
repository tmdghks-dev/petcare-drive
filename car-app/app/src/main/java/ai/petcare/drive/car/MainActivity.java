package ai.petcare.drive.car;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.DoorArea;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.constant.area.DoorValue;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.control.LightSwitch;
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit;
import kotlin.Unit;

// 화면 4개를 한 Activity에서 전환한다: 아이 선택 → 펫케어 대기 → 짖음 감지(알림·조명·음악 동시) → 진정 완료
// 데모 트리거: adb shell am broadcast -a ai.petcare.drive.car.BARK / .CALM
// ponytail: 짖음 감지는 아직 broadcast로 대체. YAMNet 마이크 감지가 붙으면 onBark()만 호출하면 됨
public class MainActivity extends AppCompatActivity {

    static final String ACTION_BARK = "ai.petcare.drive.car.BARK";
    static final String ACTION_CALM = "ai.petcare.drive.car.CALM";
    static final int[] MUSIC = {R.raw.calm_music_1, R.raw.calm_music_2};
    static final SeatArea CABIN = new SeatArea(SeatValue.ROW_1_LEFT);
    static final DoorValue[] DOORS = {DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT};
    static final long MUSIC_TAIL_MS = 2 * 60_000; // 진정 후 음악을 작게 더 틀어주는 시간

    private Vehicle vehicle;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<Dog> selected = new LinkedHashSet<>(Dog.ALL);

    private int screen;            // 현재 화면 레이아웃 id
    private long careStartAt, firstBarkAt;
    private int barkCount;
    private MediaPlayer music;
    private int track;
    private LightSwitch lightBefore;

    // 차량 값은 받는 즉시 저장해 두고, 대기 화면일 때 그린다
    private String temp = "--", battery = "--", batteryNote = "", door = "--", doorNote = "";
    private int doorsAnswered, doorsLocked;
    private boolean doorFailed;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BARK.equals(intent.getAction())) onBark();
            else onCalm();
        }
    };

    private final Runnable musicTick = new Runnable() {
        @Override
        public void run() {
            if (music == null || screen != R.layout.screen_calming) return;
            View row = findViewById(R.id.actMusic);
            ProgressBar bar = row.findViewById(R.id.progress);
            bar.setProgress(music.getCurrentPosition() * 100 / Math.max(1, music.getDuration()));
            ((TextView) row.findViewById(R.id.status)).setText(clock(music.getCurrentPosition()));
            ui.postDelayed(this, 1000);
        }
    };

    private final Runnable stopMusic = this::stopMusic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter(ACTION_BARK);
        filter.addAction(ACTION_CALM);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED);

        vehicle = new Vehicle(this);
        vehicle.initialize();
        showProfiles();
    }

    // ── 1. 함께 탈 아이 선택 ─────────────────────────────

    private void showProfiles() {
        setScreen(R.layout.screen_profiles);
        top("출발 준비", false, "여러 마리를 함께 선택할 수 있어요");

        ViewGroup row = findViewById(R.id.dogRow);
        LayoutInflater inflater = getLayoutInflater();
        for (Dog dog : Dog.ALL) {
            View card = inflater.inflate(R.layout.item_dog, row, false);
            ((ImageView) card.findViewById(R.id.photo)).setImageDrawable(circle(dog.photoRes));
            ((TextView) card.findViewById(R.id.name)).setText(dog.name);
            ((TextView) card.findViewById(R.id.info)).setText(dog.breed + " · " + dog.age);
            ViewGroup chips = card.findViewById(R.id.chips);
            chip(chips, "적정 " + dog.tempMin + "~" + dog.tempMax + "℃");
            if (dog.anxious) chip(chips, "분리불안");
            card.setOnClickListener(v -> {
                if (!selected.remove(dog)) selected.add(dog);
                markSelected(card, selected.contains(dog));
                updateSelection();
            });
            markSelected(card, selected.contains(dog));
            row.addView(card);
        }
        View add = inflater.inflate(R.layout.item_add, row, false);
        add.setOnClickListener(v -> Toast.makeText(this, "아이 추가는 보호자 앱에서 할 수 있어요", Toast.LENGTH_SHORT).show());
        row.addView(add);
        // ponytail: 카드가 많아지면 한 줄에 넘침. 4마리 이상 지원할 때 GridLayout/스크롤로 교체
        for (int i = row.getChildCount(); i < 3; i++) row.addView(new Space(this), new LinearLayout.LayoutParams(0, 0, 1f));

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            careStartAt = System.currentTimeMillis();
            showIdle();
        });
        updateSelection();
    }

    private void markSelected(View card, boolean on) {
        card.setBackgroundResource(on ? R.drawable.bg_card_selected : R.drawable.bg_card);
        card.findViewById(R.id.badge).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateSelection() {
        boolean any = !selected.isEmpty();
        text(R.id.footnote, any ? selected.size() + "마리 선택됨 · " + names() : "함께 탈 아이를 선택해 주세요");
        View start = findViewById(R.id.btnStart);
        start.setEnabled(any);
        start.setAlpha(any ? 1f : 0.4f);
    }

    // ── 2. 펫케어 대기 ───────────────────────────────────

    private void showIdle() {
        stopMusic();
        barkCount = 0;
        setScreen(R.layout.screen_idle);
        top("펫케어 대기 중", false, "짖음 감지 켜짐 · " + hhmm(careStartAt) + " 시작");
        hero(false);
        text(R.id.headline, Dog.josa(names(), "은", "는") + " 지금\n편안하게 쉬고 있어요");
        text(R.id.sub, "보호자가 곧 돌아와요");
        text(R.id.footnote, "반려견이 안전하게 보호되고 있어요. 실내 온도가 자동으로 유지됩니다.");
        findViewById(R.id.btnEnd).setOnClickListener(v -> showProfiles());
        renderVehicle();
        readVehicle();
    }

    private void renderVehicle() {
        if (screen != R.layout.screen_idle) return;
        Dog first = selected.iterator().next();
        stat(R.id.stat1, R.drawable.ic_thermo, "실내 온도", temp, "적정 " + first.tempMin + "~" + first.tempMax + "℃");
        stat(R.id.stat2, R.drawable.ic_battery, "배터리", battery, batteryNote);
        stat(R.id.stat3, R.drawable.ic_lock, "도어", door, doorNote);
    }

    // Vehicle SDK로 실내 온도, 배터리, 도어 잠금을 읽는다. 콜백은 백그라운드 스레드라 ui로 넘긴다
    private void readVehicle() {
        vehicle.getHVAC().getHvacTemperatureCurrent(CABIN, TemperatureUnit.CELSIUS,
                t -> done(() -> temp = String.format(Locale.KOREA, "%.1f℃", t)),
                e -> done(() -> temp = "확인 불가"));
        vehicle.getEvBattery().getEvBatteryStateOfCharge(
                soc -> done(() -> battery = soc + "%"),
                e -> done(() -> battery = "확인 불가"));
        // ponytail: 공조 소비전력 1.5kW 고정 가정, 실측 소비량이 있으면 그걸로 교체
        vehicle.getEvBattery().getEvBatteryLevel(
                wh -> done(() -> batteryNote = "펫케어 약 " + Math.round(wh / 1500f) + "시간 유지"),
                e -> done(() -> batteryNote = ""));
        doorsAnswered = doorsLocked = 0;
        doorFailed = false;
        for (DoorValue d : DOORS) {
            vehicle.getDoor().getDoorLockState(new DoorArea(d),
                    locked -> done(() -> door(locked, false)),
                    e -> done(() -> door(false, true)));
        }
    }

    private void door(boolean locked, boolean failed) {
        doorsAnswered++;
        if (locked) doorsLocked++;
        doorFailed |= failed;
        if (doorsAnswered < DOORS.length) return;
        if (doorFailed) {
            door = "확인 불가";
            doorNote = "";
        } else if (doorsLocked == DOORS.length) {
            door = "잠김";
            doorNote = "4개 문 모두";
        } else {
            door = "열림";
            doorNote = "잠기지 않은 문 " + (DOORS.length - doorsLocked) + "개";
        }
    }

    private Unit done(Runnable update) {
        ui.post(() -> {
            update.run();
            renderVehicle();
        });
        return Unit.INSTANCE;
    }

    // ── 3. 짖음 감지: 보호자 알림 + 조명 + 음악을 동시에 ──────────

    private void onBark() {
        if (screen == R.layout.screen_profiles) return; // 출발 전에는 무시
        barkCount++;
        if (screen == R.layout.screen_calming) {
            meta("짖음 " + barkCount + "회 · " + hhmm(firstBarkAt) + " 시작");
            return;
        }
        firstBarkAt = System.currentTimeMillis();
        showCalming();
    }

    private void showCalming() {
        setScreen(R.layout.screen_calming);
        top("짖음 감지 · 진정 중", true, "짖음 " + barkCount + "회 · " + hhmm(firstBarkAt) + " 시작");
        hero(true);
        text(R.id.headline, Dog.josa(names(), "아", "야") + ", 괜찮아");
        text(R.id.sub, "짖음이 감지되어 세 가지를 동시에 시작했어요");
        text(R.id.footnote, "보호자가 폰에서 목소리를 보내면 바로 재생돼요");

        notifyOwner();
        calmLight();
        startMusic();

        findViewById(R.id.btnMusic).setOnClickListener(v -> {
            track = (track + 1) % MUSIC.length;
            startMusic();
        });
        findViewById(R.id.btnStopCalm).setOnClickListener(v -> onCalm());
    }

    // ponytail: Firebase 연결 전이라 실제 전송은 없음. 연결되면 events에 기록 → Cloud Functions가 푸시
    private void notifyOwner() {
        action(R.id.actNotify, R.drawable.ic_bell, "보호자에게 알림",
                "\"" + Dog.josa(names(), "이", "가") + " 짖고 있어요\" · 보호자 앱 연결 후 전송", "연결 전");
    }

    private void calmLight() {
        action(R.id.actLight, R.drawable.ic_bulb, "실내 조명 차분하게", "앰비언트 라이트 · 은은한 블루 · 밝기 30%", "요청 중");
        vehicle.getLight().getAmbientLightSwitch(CABIN, s -> {
            lightBefore = s;
            return Unit.INSTANCE;
        }, e -> Unit.INSTANCE);
        vehicle.getLight().setAmbientLightSwitch(CABIN, LightSwitch.ON,
                (area, s) -> {
                    vehicle.getLight().setAmbientLightBrightnessColor(CABIN, 0x00AAD2, (a, c) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                    vehicle.getLight().setAmbientLightBrightness(CABIN, 30, (a, b) -> Unit.INSTANCE, e -> Unit.INSTANCE);
                    return lightStatus("켜짐", null);
                },
                e -> lightStatus("미지원", "이 차량에서는 아직 조명을 제어할 수 없어요"));
    }

    private Unit lightStatus(String status, String sub) {
        ui.post(() -> {
            if (screen != R.layout.screen_calming) return;
            View row = findViewById(R.id.actLight);
            ((TextView) row.findViewById(R.id.status)).setText(status);
            if (sub != null) ((TextView) row.findViewById(R.id.sub)).setText(sub);
        });
        return Unit.INSTANCE;
    }

    private void restoreLight() {
        if (lightBefore == null) return;
        vehicle.getLight().setAmbientLightSwitch(CABIN, lightBefore, (a, s) -> Unit.INSTANCE, e -> Unit.INSTANCE);
        lightBefore = null;
    }

    private void startMusic() {
        stopMusic();
        music = MediaPlayer.create(this, MUSIC[track]);
        music.setLooping(true);
        music.start();
        action(R.id.actMusic, R.drawable.ic_music, "안정 음악 재생 중", null, "0:00");
        findViewById(R.id.actMusic).findViewById(R.id.progress).setVisibility(View.VISIBLE);
        ui.post(musicTick);
    }

    private void stopMusic() {
        ui.removeCallbacks(musicTick);
        ui.removeCallbacks(stopMusic);
        if (music != null) music.release();
        music = null;
    }

    // ── 4. 진정 완료 ─────────────────────────────────────

    private void onCalm() {
        if (screen != R.layout.screen_calming) return;
        long now = System.currentTimeMillis();
        restoreLight();
        if (music != null) {
            music.setVolume(0.3f, 0.3f);
            ui.postDelayed(stopMusic, MUSIC_TAIL_MS);
        }

        setScreen(R.layout.screen_calmed);
        top("진정 완료", false, hhmm(now) + " · 진정 완료");
        hero(false);
        text(R.id.headline, Dog.josa(names(), "이", "가") + " 다시\n편안해졌어요");
        text(R.id.sub, "조명은 원래대로 돌아오고, 음악은 작게 이어서 재생돼요");
        stat(R.id.stat1, 0, "짖음", barkCount + "회", "");
        stat(R.id.stat2, 0, "진정까지", duration(now - firstBarkAt), "");
        stat(R.id.stat3, 0, "혼자 있던 시간", duration(now - careStartAt), "");
        text(R.id.footnote, "보호자 앱이 연결되면 이 기록이 리포트로 저장돼요");
        findViewById(R.id.btnBackIdle).setOnClickListener(v -> showIdle());
    }

    // ── 공통 화면 조각 ───────────────────────────────────

    private void setScreen(int layout) {
        ui.removeCallbacks(musicTick);
        screen = layout;
        setContentView(layout);
    }

    private void top(String label, boolean strong, String meta) {
        View pill = findViewById(R.id.pill);
        pill.setBackgroundResource(strong ? R.drawable.bg_pill_strong : R.drawable.bg_pill_soft);
        findViewById(R.id.pillDot).setBackgroundResource(strong ? R.drawable.dot_white : R.drawable.dot_accent);
        TextView text = findViewById(R.id.pillText);
        text.setText(label);
        text.setTextColor(strong ? 0xFFFFFFFF : ContextCompat.getColor(this, R.color.primary));
        meta(meta);
    }

    private void meta(String meta) {
        text(R.id.meta, meta);
    }

    private void hero(boolean strongRing) {
        findViewById(R.id.ring).setBackgroundResource(strongRing ? R.drawable.bg_ring_strong : R.drawable.bg_ring);
        ((ImageView) findViewById(R.id.photo)).setImageDrawable(circle(selected.iterator().next().photoRes));
    }

    private void stat(int id, int icon, String label, String value, String note) {
        View card = findViewById(id);
        card.findViewById(R.id.tile).setVisibility(icon == 0 ? View.GONE : View.VISIBLE);
        if (icon != 0) ((ImageView) card.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) card.findViewById(R.id.label)).setText(label);
        ((TextView) card.findViewById(R.id.value)).setText(value);
        TextView n = card.findViewById(R.id.note);
        n.setText(note);
        n.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void action(int id, int icon, String title, String sub, String status) {
        View row = findViewById(id);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView s = row.findViewById(R.id.sub);
        s.setText(sub);
        s.setVisibility(sub == null ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.status)).setText(status);
    }

    private void chip(ViewGroup parent, String label) {
        TextView chip = (TextView) getLayoutInflater().inflate(R.layout.item_chip, parent, false);
        chip.setText(label);
        parent.addView(chip);
    }

    private void text(int id, String value) {
        ((TextView) findViewById(id)).setText(value);
    }

    private String names() {
        if (selected.isEmpty()) return "";
        String first = selected.iterator().next().name;
        return selected.size() == 1 ? first : first + " 외 " + (selected.size() - 1) + "마리";
    }

    // 세로 사진을 가운데 정사각형으로 잘라 동그랗게
    private RoundedBitmapDrawable circle(int res) {
        Bitmap b = BitmapFactory.decodeResource(getResources(), res);
        int s = Math.min(b.getWidth(), b.getHeight());
        Bitmap square = Bitmap.createBitmap(b, (b.getWidth() - s) / 2, (b.getHeight() - s) / 2, s, s);
        RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(getResources(), square);
        d.setCircular(true);
        return d;
    }

    private static String hhmm(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(ms));
    }

    private static String clock(int ms) {
        int s = ms / 1000;
        return s / 60 + ":" + String.format(Locale.KOREA, "%02d", s % 60);
    }

    static String duration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "초";
        return s % 60 == 0 || s >= 600 ? s / 60 + "분" : s / 60 + "분 " + s % 60 + "초";
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);
        stopMusic();
        ui.removeCallbacksAndMessages(null);
        vehicle.release();
        super.onDestroy();
    }
}
