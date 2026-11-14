package ai.petcare.drive.car;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.widget.ProgressBar;
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
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.control.LightSwitch;
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit;
import kotlin.Unit;

// 화면 설계 v2(docs/design/ui-v2/car.html)를 한 Activity에서 전환한다:
// ① 펫케어 시작 → ② 펫케어 대기 ⇄ ③ 달래는 중(알림·조명·음악 동시)
// 데모 트리거: adb shell am broadcast -a ai.petcare.drive.car.BARK / .CALM / .ARRIVE
// 짖음 감지: BarkDetector(마이크 + YAMNet). adb BARK/CALM은 시연용으로 남겨 둔다
// ponytail: 도착 감지도 broadcast로 대체. NaviHelper 도착 이벤트가 붙으면 showStart(true)만 호출하면 됨
public class MainActivity extends AppCompatActivity implements BarkDetector.Listener {

    static final String ACTION_BARK = "ai.petcare.drive.car.BARK";
    static final String ACTION_CALM = "ai.petcare.drive.car.CALM";
    static final String ACTION_ARRIVE = "ai.petcare.drive.car.ARRIVE";
    // 데모: 에뮬레이터는 실내 온도를 바꿀 수 없어서 신호로 대신한다. --ef temp 29.5 / SAFE로 해제
    static final String ACTION_DANGER = "ai.petcare.drive.car.DANGER";
    static final String ACTION_SAFE = "ai.petcare.drive.car.SAFE";
    // 데모: 휴식 알림. DRIVE --ei min 60 → 60분 달린 것으로, REST → 5분 쉰 것으로
    static final String ACTION_DRIVE = "ai.petcare.drive.car.DRIVE";
    static final String ACTION_REST = "ai.petcare.drive.car.REST";
    static final int[] MUSIC = {R.raw.calm_music_1, R.raw.calm_music_2};
    static final SeatArea CABIN = new SeatArea(SeatValue.ROW_1_LEFT);
    static final long MUSIC_TAIL_MS = 2 * 60_000; // 진정 후 음악을 작게 더 틀어주는 시간
    static final float IDLE_VOLUME = 0.35f;       // 기다리는 동안 틀어 두는 음악 크기
    static final long IDLE_TICK_MS = 30_000;      // 대기 화면에서 차량 값·혼자 있은 시간을 다시 그리는 간격

    private Vehicle vehicle;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<Dog> selected = new LinkedHashSet<>(Dog.ALL);

    private int screen;            // 현재 화면 레이아웃 id
    private long careStartAt, firstBarkAt;
    private int barkCount;
    private MediaPlayer music;
    private MediaPlayer voice;     // 보호자 목소리 (음악과 따로)
    private int voicePlays;
    private int track;
    private LightSwitch lightBefore;
    private BarkDetector detector;
    private RestReminder rest;
    private TextView bannerView;
    private final Runnable hideBanner = () -> bannerView.setVisibility(View.GONE);
    // 마이크 권한을 물은 뒤 결과를 받는다. 허용하면 바로 듣기 시작
    private final ActivityResultLauncher<String> askMic = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) Toast.makeText(this, "마이크 권한이 없어 짖음을 감지할 수 없어요", Toast.LENGTH_LONG).show();
                else if (screen != R.layout.screen_start) detector.start();
            });

    // 차량 값은 받는 즉시 저장해 두고, 대기 화면일 때 그린다. 모르면 NaN / -1
    private float tempC = Float.NaN;
    private int soc = -1;
    private float batteryWh = -1;
    private android.content.SharedPreferences prefs;
    private float demoTemp = Float.NaN;   // 데모 신호로 정한 실내 온도. 없으면 NaN
    private String dangerShown;           // 지금 ④에 보이는 위험 이유
    private long dangerAt;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_BARK.equals(action)) onBark();
            else if (ACTION_CALM.equals(action)) onCalm();
            else if (ACTION_ARRIVE.equals(action)) showStart(true);
            else if (ACTION_DANGER.equals(action)) {
                demoTemp = intent.getFloatExtra("temp", 29.5f);
                tempC = demoTemp;
                checkDanger();
            } else if (ACTION_SAFE.equals(action)) {
                demoTemp = Float.NaN;
                readVehicle();
            } else if (ACTION_DRIVE.equals(action)) rest.demoDrive(intent.getIntExtra("min", 60));
            else if (ACTION_REST.equals(action)) rest.demoRest();
        }
    };

    private final Runnable musicTick = new Runnable() {
        @Override
        public void run() {
            if (music == null || screen != R.layout.screen_calming) return;
            View row = findViewById(R.id.actMusic);
            ProgressBar bar = row.findViewById(R.id.progress);
            bar.setProgress(music.getCurrentPosition() * 100 / Math.max(1, music.getDuration()));
            ((TextView) row.findViewById(R.id.status)).setText(clock(music.getCurrentPosition()) + " / " + clock(music.getDuration()));
            ui.postDelayed(this, 1000);
        }
    };

    private final Runnable idleTick = new Runnable() {
        @Override
        public void run() {
            if (screen != R.layout.screen_idle && screen != R.layout.screen_danger) return;
            readVehicle();
            renderVehicle();
            ui.postDelayed(this, IDLE_TICK_MS);
        }
    };

    private final Runnable stopMusic = this::stopMusic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter(ACTION_BARK);
        filter.addAction(ACTION_CALM);
        filter.addAction(ACTION_ARRIVE);
        filter.addAction(ACTION_DANGER);
        filter.addAction(ACTION_SAFE);
        filter.addAction(ACTION_DRIVE);
        filter.addAction(ACTION_REST);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED);

        vehicle = new Vehicle(this);
        vehicle.initialize();
        detector = new BarkDetector(this, this);
        rest = new RestReminder(this, vehicle, this::names, this::banner);
        rest.start();
        prefs = getSharedPreferences("petcare", MODE_PRIVATE);
        FirebaseManager.getInstance().init(this, carId());
        FirebaseManager.getInstance().listenForPairing(() -> ui.post(this::onPaired));
        FirebaseManager.getInstance().listenForCommands((type, voiceUrl, params) -> {
            if (!"PLAY_VOICE".equals(type) || voiceUrl == null || !voiceUrl.startsWith("http")) return;
            FirebaseManager.getInstance().downloadVoiceFile(this, voiceUrl, new FirebaseManager.FileCallback() {
                @Override
                public void onSuccess(java.io.File file) {
                    ui.post(() -> playVoice(file));
                }

                @Override
                public void onError(Exception e) {
                    ui.post(() -> Toast.makeText(MainActivity.this, "목소리를 받지 못했어요", Toast.LENGTH_SHORT).show());
                }
            });
        });
        if (paired()) showStart(false);
        else showConnect();
    }

    // ── ⑤ 폰 연결 (처음 한 번) ────────────────────────────

    // 이 차의 ID. 처음 켤 때 한 번 만들어 저장한다. 폰은 QR로 이 ID를 받아 vehicles/{carId}를 함께 쓴다
    private String carId() {
        String id = prefs.getString("carId", null);
        if (id == null) {
            id = "car_" + java.util.UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString("carId", id).apply();
        }
        return id;
    }

    private boolean paired() {
        return prefs.getBoolean("paired", false);
    }

    // ponytail: QR에 차 ID만 담는다. 디자인의 '5분짜리 연결 코드'는 폰 앱이 코드를 확인하게 되면 추가
    private void showConnect() {
        setScreen(R.layout.screen_connect);
        bar(false);
        String id = carId();
        ((ImageView) findViewById(R.id.qr)).setImageBitmap(qr("petcare://pair?car=" + id, 840));
        text(R.id.carCode, "차 코드 " + id.substring(4).toUpperCase(Locale.ROOT));
        findViewById(R.id.btnLater).setOnClickListener(v -> showStart(false));
    }

    // 폰 앱이 vehicles/{carId}/info에 연결 기록을 남기면 불린다
    private void onPaired() {
        boolean first = !paired();
        prefs.edit().putBoolean("paired", true).apply();
        if (screen == R.layout.screen_connect) {
            showStart(false);
            Toast.makeText(this, "보호자 폰과 연결됐어요", Toast.LENGTH_LONG).show();
        } else if (first && findViewById(R.id.link) != null) {
            text(R.id.link, "보호자 폰 연결됨");
            findViewById(R.id.linkDot).setBackgroundResource(R.drawable.dot_primary);
        }
    }

    private static Bitmap qr(String content, int size) {
        try {
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    b.setPixel(x, y, m.get(x, y) ? 0xFF0F1115 : 0xFFFFFFFF);
            return b;
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException(e); // 짧은 글자라 일어나지 않는다
        }
    }

    // ── ① 펫케어 시작 ────────────────────────────────────

    private void showStart(boolean arrived) {
        detector.stop(); // ① 화면으로 돌아오면 듣기를 멈춘다
        stopMusic();
        stopVoice();
        restoreLight();
        setScreen(R.layout.screen_start);
        bar(false);
        findViewById(R.id.eyebrow).setVisibility(arrived ? View.VISIBLE : View.GONE);

        ViewGroup row = findViewById(R.id.dogRow);
        LayoutInflater inflater = getLayoutInflater();
        for (Dog dog : Dog.ALL) {
            View card = inflater.inflate(R.layout.item_dog, row, false);
            ((ImageView) card.findViewById(R.id.photo)).setImageDrawable(circle(dog.photoRes));
            ((TextView) card.findViewById(R.id.name)).setText(dog.name);
            ((TextView) card.findViewById(R.id.info)).setText(
                    "적정 " + dog.tempMin + "~" + dog.tempMax + "°C" + (dog.anxious ? " · 분리불안" : ""));
            card.setOnClickListener(v -> {
                if (!selected.remove(dog)) selected.add(dog);
                markSelected(card, selected.contains(dog));
                updateSelection();
            });
            markSelected(card, selected.contains(dog));
            row.addView(card);
        }
        // ponytail: 카드가 많아지면 한 줄에 넘침. 3마리 이상 지원할 때 가로 스크롤로 교체
        View add = inflater.inflate(R.layout.item_add, row, false);
        add.setOnClickListener(v -> Toast.makeText(this, "아이 추가는 보호자 앱에서 할 수 있어요", Toast.LENGTH_SHORT).show());
        row.addView(add);

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            careStartAt = System.currentTimeMillis();
            showIdle(null);
            startListening();
            if (idleMusic()) startIdleMusic();
        });
        findViewById(R.id.btnNo).setOnClickListener(v -> moveTaskToBack(true));
        setupMusicOption();
        updateSelection();
    }

    // ① 오른쪽 아래 선택: 기다리는 동안 음악 틀기 + 어떤 음악. 다음에도 기억한다
    private void setupMusicOption() {
        androidx.appcompat.widget.SwitchCompat sw = findViewById(R.id.musicSwitch);
        sw.setChecked(idleMusic());
        findViewById(R.id.musicRow).setOnClickListener(v -> sw.toggle());
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("idleMusic", on).apply();
            renderTracks();
        });
        findViewById(R.id.track1).setOnClickListener(v -> pickTrack(0));
        findViewById(R.id.track2).setOnClickListener(v -> pickTrack(1));
        track = prefs.getInt("track", 0);
        renderTracks();
    }

    private void pickTrack(int t) {
        track = t;
        prefs.edit().putInt("track", t).apply();
        renderTracks();
    }

    private void renderTracks() {
        findViewById(R.id.trackRow).setVisibility(idleMusic() ? View.VISIBLE : View.GONE);
        int[] ids = {R.id.track1, R.id.track2};
        for (int i = 0; i < ids.length; i++) {
            TextView t = findViewById(ids[i]);
            boolean on = i == track;
            t.setBackgroundResource(on ? R.drawable.bg_pill_soft : R.drawable.bg_btn_end);
            t.setTextColor(ContextCompat.getColor(this, on ? R.color.primary : R.color.ink2));
        }
    }

    private boolean idleMusic() {
        return prefs.getBoolean("idleMusic", false);
    }

    // 대기 중 음악: 고른 곡을 작게 반복. 짖으면 ③에서 같은 곡을 크게 튼다
    private void startIdleMusic() {
        stopMusic();
        music = MediaPlayer.create(this, MUSIC[track]);
        music.setLooping(true);
        music.setVolume(IDLE_VOLUME, IDLE_VOLUME);
        music.start();
    }

    private void markSelected(View card, boolean on) {
        card.setBackgroundResource(on ? R.drawable.bg_card_selected : R.drawable.bg_card);
        card.findViewById(R.id.badge).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateSelection() {
        boolean any = !selected.isEmpty();
        text(R.id.who, any ? names() : "아이를 골라 주세요");
        Dog first = any ? selected.iterator().next() : null;
        text(R.id.check1, first == null ? "실내 온도를 알맞게 지켜요"
                : "실내 온도를 " + first.tempMin + "~" + first.tempMax + "°C로 지켜요");
        View start = findViewById(R.id.btnStart);
        start.setEnabled(any);
        start.setAlpha(any ? 1f : 0.4f);
    }

    // ── ② 펫케어 대기 ───────────────────────────────────

    // notice: 위쪽 한 줄 알림 (진정 완료 등). 없으면 null
    private void showIdle(String notice) {
        barkCount = 0;
        setScreen(R.layout.screen_idle);
        bar(true);
        Dog first = selected.iterator().next();
        ImageView bust = findViewById(R.id.bust);
        if (first.bustRes != 0) bust.setImageResource(first.bustRes);
        else bust.setImageDrawable(circle(first.photoRes));
        text(R.id.name, names());
        findViewById(R.id.notice).setVisibility(notice == null ? View.GONE : View.VISIBLE);
        if (notice != null) text(R.id.noticeText, notice);
        renderVehicle();
        ui.post(idleTick);
    }

    private void renderVehicle() {
        if (screen != R.layout.screen_idle) return;
        Dog first = selected.iterator().next();

        String note;
        int noteColor;
        if (Float.isNaN(tempC)) {
            note = "확인 중";
            noteColor = R.color.muted;
        } else if (tempC > first.tempMax) {
            note = "높아요";
            noteColor = R.color.red;
        } else if (tempC < first.tempMin) {
            note = "낮아요";
            noteColor = R.color.amber;
        } else {
            note = "알맞아요";
            noteColor = R.color.primary;
        }
        stat(R.id.stat1, R.drawable.ic_thermo, "실내 온도",
                Float.isNaN(tempC) ? "--" : String.format(Locale.KOREA, "%.1f°C", tempC), note, noteColor);

        long min = (System.currentTimeMillis() - careStartAt) / 60_000;
        stat(R.id.stat2, R.drawable.ic_clock, "혼자 있은 시간",
                min < 60 ? min + "분" : min / 60 + "시간 " + min % 60 + "분", hhmm(careStartAt) + "부터", R.color.muted);

        // ponytail: 공조 소비전력 1.5kW 고정 가정, 실측 소비량이 있으면 그걸로 교체
        float hours = batteryWh / 1500f;
        String keep = batteryWh < 0 ? "--" : hours >= 1 ? "약 " + Math.round(hours) + "시간" : "약 " + Math.round(hours * 60) + "분";
        stat(R.id.stat3, R.drawable.ic_battery, "펫케어 유지", keep, soc < 0 ? "배터리 확인 중" : "배터리 " + soc + "%", R.color.muted);
    }

    // Vehicle SDK로 실내 온도와 배터리를 읽는다. 콜백은 백그라운드 스레드라 ui로 넘긴다
    private void readVehicle() {
        vehicle.getHVAC().getHvacTemperatureCurrent(CABIN, TemperatureUnit.CELSIUS,
                t -> done(() -> tempC = Float.isNaN(demoTemp) ? t : demoTemp),
                e -> done(() -> tempC = Float.NaN));
        vehicle.getEvBattery().getEvBatteryStateOfCharge(
                s -> done(() -> soc = Math.round(s)),
                e -> done(() -> soc = -1));
        vehicle.getEvBattery().getEvBatteryLevel(
                wh -> done(() -> batteryWh = wh),
                e -> done(() -> batteryWh = -1));
    }

    private Unit done(Runnable update) {
        ui.post(() -> {
            update.run();
            renderVehicle();
            checkDanger();
            // 반려견 기준 값만 올린다 (문 잠금·위치는 기본 앱 몫이라 뺌)
            FirebaseManager.getInstance().uploadCarStatus(tempC, soc, batteryWh, stateName());
        });
        return Unit.INSTANCE;
    }

    // ── ④ 위험: 온도·배터리가 위험하면 바로 바꾸고, 해결되면 대기로 ──────────

    // 위험하지 않으면 null. 첫 반려견의 적정 범위 기준
    // ponytail: 여유 폭(+3°C / -5°C, 1시간)은 감으로 정함. 수의사 기준이 있으면 그걸로
    private String dangerReason() {
        if (selected.isEmpty()) return null;
        Dog d = selected.iterator().next();
        if (!Float.isNaN(tempC) && tempC > d.tempMax + 3) return "hot";
        if (!Float.isNaN(tempC) && tempC < d.tempMin - 5) return "cold";
        if ((batteryWh >= 0 && batteryWh < 1500) || (soc >= 0 && soc < 10)) return "battery";
        return null;
    }

    private void checkDanger() {
        if (screen == R.layout.screen_start) return;
        String reason = dangerReason();
        if (reason == null) {
            if (screen == R.layout.screen_danger) showIdle("다시 안전해졌어요 · " + hhmm(System.currentTimeMillis()));
            return;
        }
        if (screen != R.layout.screen_danger || !reason.equals(dangerShown)) showDanger(reason);
    }

    private void showDanger(String reason) {
        boolean first = screen != R.layout.screen_danger;
        if (first) dangerAt = System.currentTimeMillis();
        dangerShown = reason;
        stopMusic();
        stopVoice();
        restoreLight();
        setScreen(R.layout.screen_danger);
        bar(true);
        Dog d = selected.iterator().next();
        ImageView bust = findViewById(R.id.bust);
        if (d.bustRes != 0) bust.setImageResource(d.bustRes);
        else bust.setImageDrawable(circle(d.photoRes));
        text(R.id.name, names());
        text(R.id.noticeText, hhmm(dangerAt) + " · 보호자에게 긴급 알림을 보냈어요");
        String range = d.name + " 적정 " + d.tempMin + "~" + d.tempMax + "°C";
        // ponytail: 창문 2cm 환기(C-17)는 Window 제어 권한 승인 전이라 문구에서 뺌. 승인되면 "창문을 2cm 열어 두었어요" 추가
        if ("battery".equals(reason)) {
            text(R.id.headline, "배터리가 부족해요");
            text(R.id.value, soc >= 0 ? soc + "%" : "--");
            text(R.id.range, batteryWh >= 0 ? "펫케어 약 " + Math.round(batteryWh / 1500f * 60) + "분 남음" : "");
            text(R.id.detail, "곧 에어컨을 켜 둘 수 없어요.\n실내 온도 " + (Float.isNaN(tempC) ? "--" : String.format(Locale.KOREA, "%.1f°C", tempC)) + " · " + range);
        } else {
            text(R.id.headline, "hot".equals(reason) ? "실내 온도가 높아요" : "실내 온도가 낮아요");
            text(R.id.value, String.format(Locale.KOREA, "%.1f°C", tempC));
            text(R.id.range, range);
            text(R.id.detail, "hot".equals(reason) ? "에어컨을 켜 두었지만 온도가 계속 오르고 있어요." : "난방을 켜 두었지만 온도가 계속 내려가고 있어요.");
        }
        ui.post(idleTick);
        if (first) FirebaseManager.getInstance().reportDanger(reason, tempC, soc);
    }

    // ── ③ 짖음 감지: 보호자 알림 + 조명 + 음악을 동시에 ──────────

    @Override
    public void onBark() {
        if (screen == R.layout.screen_start) return; // 펫케어 시작 전에는 무시
        barkCount++;
        FirebaseManager.getInstance().reportBarkEvent(barkCount, hhmm(System.currentTimeMillis()));
        if (screen == R.layout.screen_danger) return; // 위험 화면이 먼저. 짖음 횟수만 센다
        if (screen == R.layout.screen_calming) {
            meta(barkMeta());
            return;
        }
        firstBarkAt = System.currentTimeMillis();
        voicePlays = 0;
        showCalming();
    }

    private void showCalming() {
        setScreen(R.layout.screen_calming);
        bar(true);
        showDog();
        meta(barkMeta());
        text(R.id.headline, Dog.josa(names(), "을", "를") + " 달래는 중이에요");
        // 알림은 Firebase에 이벤트를 올렸을 때만 '알렸어요'라고 쓴다
        text(R.id.sub, FirebaseManager.getInstance().isReady()
                ? "보호자에게 알렸어요. 지금 이렇게 달래고 있어요." : "지금 이렇게 달래고 있어요.");
        text(R.id.footnote, "반려견이 안전하게 보호되고 있어요"
                + (Float.isNaN(tempC) ? "" : String.format(Locale.KOREA, " · 실내 %.1f°C", tempC)));

        action(R.id.actVoice, R.drawable.ic_voice, "보호자 목소리", "\"금방 올게\"", "준비 중");
        calmLight();
        startMusic();
        // ponytail: 자동으로 틀 목소리는 앱에 넣어 둔 샘플. 보호자 앱 ⑤에서 고른 목소리(Storage)로 교체
        playVoice(MediaPlayer.create(this, R.raw.owner_voice), "\"금방 올게\"");
    }

    private String barkMeta() {
        return "짖음 감지 · " + hhmm(firstBarkAt) + (barkCount > 1 ? " · " + barkCount + "회" : "");
    }

    private void calmLight() {
        action(R.id.actLight, R.drawable.ic_bulb, "조명", "차분한 파란빛", "요청 중");
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
                e -> lightStatus("미지원", "이 차에서는 아직 바꿀 수 없어요"));
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
        action(R.id.actMusic, R.drawable.ic_music, "안정 음악", null, "0:00");
        findViewById(R.id.actMusic).findViewById(R.id.progress).setVisibility(View.VISIBLE);
        ui.post(musicTick);
    }

    private void stopMusic() {
        ui.removeCallbacks(musicTick);
        ui.removeCallbacks(stopMusic);
        if (music != null) music.release();
        music = null;
    }

    // 조용해지면 대기 화면으로 돌아가며 한 줄로 요약한다. 음악은 작게 2분 더
    private void onCalm() {
        if (screen != R.layout.screen_calming) return;
        restoreLight();
        if (music != null && idleMusic()) {
            music.setVolume(IDLE_VOLUME, IDLE_VOLUME); // 음악 틀어 두기를 골랐으면 작게 계속
        } else if (music != null) {
            music.setVolume(0.3f, 0.3f);
            ui.postDelayed(stopMusic, MUSIC_TAIL_MS);
        }
        String took = duration(System.currentTimeMillis() - firstBarkAt);
        FirebaseManager.getInstance().reportCalmCompleted(took);
        showIdle("진정했어요 · 짖음 " + barkCount + "회, " + took);
    }

    // ── 짖음 감지 (마이크) ──────────────────────────────

    @Override
    public void onQuiet() {
        onCalm(); // ③ 달래는 중일 때만 동작한다 (onCalm 첫 줄에서 확인)
    }

    // 권한이 있으면 바로 듣고, 없으면 먼저 묻는다
    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) detector.start();
        else askMic.launch(Manifest.permission.RECORD_AUDIO);
    }

    // 펫케어 상태: 시작 전 / 차분 / 달래는 중
    private String stateName() {
        if (screen == R.layout.screen_danger) return "danger";
        if (screen == R.layout.screen_calming) return "calming";
        return screen == R.layout.screen_idle ? "calm" : "off";
    }

    // 보호자가 폰에서 보낸 목소리 파일
    private void playVoice(java.io.File file) {
        playVoice(MediaPlayer.create(this, android.net.Uri.fromFile(file)), "폰에서 보낸 목소리");
    }

    // 목소리를 트는 동안 음악은 작게 줄였다가 끝나면 되돌린다
    private void playVoice(MediaPlayer player, String label) {
        stopVoice();
        if (player == null) {
            voiceStatus(label, "재생 못 함");
            return;
        }
        voice = player;
        voicePlays++;
        if (music != null) music.setVolume(0.2f, 0.2f);
        voice.setOnCompletionListener(mp -> {
            stopVoice();
            if (music != null && screen == R.layout.screen_calming) music.setVolume(1f, 1f);
            voiceStatus(label, "들려줬어요");
        });
        voice.start();
        voiceStatus(label + (voicePlays > 1 ? " · " + voicePlays + "번째" : ""), "재생 중");
        if (screen != R.layout.screen_calming) Toast.makeText(this, "보호자 목소리를 들려주고 있어요", Toast.LENGTH_SHORT).show();
    }

    private void voiceStatus(String sub, String status) {
        if (screen != R.layout.screen_calming) return;
        View row = findViewById(R.id.actVoice);
        ((TextView) row.findViewById(R.id.sub)).setText(sub);
        ((TextView) row.findViewById(R.id.status)).setText(status);
    }

    private void stopVoice() {
        if (voice != null) voice.release();
        voice = null;
    }

    // ── 공통 화면 조각 ───────────────────────────────────

    private void setScreen(int layout) {
        ui.removeCallbacks(musicTick);
        ui.removeCallbacks(idleTick);
        screen = layout;
        setContentView(layout);
    }

    // v2 위쪽 줄. caring: 펫케어 중이면 '펫케어 끝내기'를 보인다
    // 연결 전이면 '보호자 앱 연결 전'을 누르면 ⑤ 연결 화면으로 간다 (펫케어 중에는 막음)
    private void bar(boolean caring) {
        boolean on = paired();
        text(R.id.link, on ? "보호자 폰 연결됨" : "보호자 앱 연결 전");
        findViewById(R.id.linkDot).setBackgroundResource(on ? R.drawable.dot_primary : R.drawable.dot_muted);
        findViewById(R.id.link).setOnClickListener(on || caring ? null : v -> showConnect());
        View end = findViewById(R.id.btnEnd);
        end.setVisibility(caring ? View.VISIBLE : View.GONE);
        end.setOnClickListener(v -> showStart(false));
    }

    // 어느 화면이든 위쪽 가운데에 잠깐 띄우는 알림. 운전 중이라 누를 것 없이 10초 뒤 사라진다
    // decorView에 붙여 setContentView로 화면이 바뀌어도 남는다
    private void banner(String msg) {
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        if (bannerView == null) {
            bannerView = new TextView(this);
            bannerView.setTextSize(32);
            bannerView.setTextColor(0xFFFFFFFF);
            bannerView.setTypeface(null, android.graphics.Typeface.BOLD);
            bannerView.setBackgroundResource(R.drawable.bg_banner);
            bannerView.setPadding(56, 32, 56, 32);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
            lp.topMargin = 140;
            decor.addView(bannerView, lp);
        }
        bannerView.setText(msg); // 새 알림이 오면 겹치지 않고 글자만 바꾼다
        bannerView.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideBanner);
        ui.postDelayed(hideBanner, 10_000);
    }

    private void meta(String meta) {
        text(R.id.meta, meta);
    }

    // 화면 왼쪽 반려견 사진과 이름 (②③④ 공통)
    private void showDog() {
        Dog d = selected.iterator().next();
        ImageView bust = findViewById(R.id.bust);
        if (d.bustRes != 0) bust.setImageResource(d.bustRes);
        else bust.setImageDrawable(circle(d.photoRes));
        text(R.id.name, names());
    }

    private void stat(int id, int icon, String label, String value, String note, int noteColor) {
        View card = findViewById(id);
        ((ImageView) card.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) card.findViewById(R.id.label)).setText(label);
        ((TextView) card.findViewById(R.id.value)).setText(value);
        TextView n = card.findViewById(R.id.note);
        n.setText(note);
        n.setTextColor(ContextCompat.getColor(this, noteColor));
        n.setTypeface(null, noteColor == R.color.muted ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
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
        detector.stop();
        rest.stop();
        unregisterReceiver(receiver);
        stopMusic();
        stopVoice();
        ui.removeCallbacksAndMessages(null);
        vehicle.release();
        super.onDestroy();
    }
}
