package ai.petcare.drive.car;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.model.area.SeatArea;
import ai.pleos.playground.vehicle.constant.area.SeatValue;
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit;
import kotlin.Unit;

// 펫케어 대기 화면. 짖음이 감지될 때마다 진정 단계를 1→2→3으로 올린다.
// 데모 트리거: adb shell am broadcast -a ai.petcare.drive.car.BARK / .CALM
// ponytail: 짖음 감지는 아직 broadcast로 대체. YAMNet 마이크 감지가 붙으면 onBark()만 호출하면 됨.
public class MainActivity extends AppCompatActivity {

    static final String ACTION_BARK = "ai.petcare.drive.car.BARK";
    static final String ACTION_CALM = "ai.petcare.drive.car.CALM";
    static final String DOG = "콩이";

    private Vehicle vehicle;
    private View root, glow, face, listenDot;
    private TextView stage, listen, headline, sub, banner, temp, battery, hours;
    private MediaPlayer voice;
    private ObjectAnimator breathing;

    private int level = 0;      // 0 = 대기, 1~3 = 진정 단계
    private int barkCount = 0;
    private long firstBarkAt = 0;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BARK.equals(intent.getAction())) onBark();
            else onCalm();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        glow = findViewById(R.id.glow);
        face = findViewById(R.id.face);
        listenDot = findViewById(R.id.listenDot);
        stage = findViewById(R.id.stage);
        listen = findViewById(R.id.listen);
        headline = findViewById(R.id.headline);
        sub = findViewById(R.id.sub);
        banner = findViewById(R.id.banner);
        temp = findViewById(R.id.temp);
        battery = findViewById(R.id.battery);
        hours = findViewById(R.id.hours);

        IntentFilter filter = new IntentFilter(ACTION_BARK);
        filter.addAction(ACTION_CALM);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED);

        pulse(listenDot);
        showIdle(null);

        vehicle = new Vehicle(this);
        vehicle.initialize();
        readVehicle();
    }

    // Vehicle SDK로 실내 온도와 배터리를 읽어 하단 칩에 표시
    private void readVehicle() {
        vehicle.getHVAC().getHvacTemperatureCurrent(new SeatArea(SeatValue.ROW_1_LEFT), TemperatureUnit.CELSIUS,
                t -> {
                    show(temp, String.format("실내 온도 %.1f℃", t));
                    return Unit.INSTANCE;
                },
                e -> {
                    show(temp, "실내 온도 확인 불가");
                    return Unit.INSTANCE;
                });
        vehicle.getEvBattery().getEvBatteryStateOfCharge(
                soc -> {
                    show(battery, "배터리 " + soc + "%");
                    return Unit.INSTANCE;
                },
                e -> {
                    show(battery, "배터리 확인 불가");
                    return Unit.INSTANCE;
                });
        vehicle.getEvBattery().getEvBatteryLevel(
                wh -> {
                    // ponytail: 공조 소비전력 1.5kW 고정 가정, 실측 소비량이 있으면 그걸로 교체
                    show(hours, String.format("펫케어 약 %d시간 유지 가능", Math.round(wh / 1500f)));
                    return Unit.INSTANCE;
                },
                e -> {
                    show(hours, "유지 시간 계산 불가");
                    return Unit.INSTANCE;
                });
    }

    private void onBark() {
        if (barkCount++ == 0) firstBarkAt = System.currentTimeMillis();
        level = Math.min(level + 1, 3);
        listen.setText("짖음 감지됨 · " + barkCount + "회");
        if (level == 1) showOwner();
        else showNight(level == 3);
    }

    private void onCalm() {
        if (level == 0) return;
        long sec = (System.currentTimeMillis() - firstBarkAt) / 1000;
        showIdle(String.format("진정 완료 · 짖음 %d회 · %d분 %d초 만에 안정", barkCount, sec / 60, sec % 60));
        level = 0;
        barkCount = 0;
    }

    private void showIdle(String summary) {
        stopVoice();
        stopBreathing();
        root.setBackgroundColor(color(R.color.calm_bg));
        tint(color(R.color.calm_text), color(R.color.calm_sub));
        face.setVisibility(View.VISIBLE);
        glow.setAlpha(0);
        stage.setText("펫케어 대기 모드");
        listen.setText("짖음 감지 중");
        headline.setText(DOG + "는 지금 안전하게 쉬고 있어요");
        sub.setText("보호자가 곧 돌아와요");
        setBanner(summary, R.color.ok);
    }

    // 1단계: 보호자 목소리 재생
    private void showOwner() {
        root.setBackgroundColor(color(R.color.owner_bg));
        tint(color(R.color.calm_text), color(R.color.calm_sub));
        stage.setText("1단계 · 보호자 목소리");
        headline.setText("\"" + DOG + "야, 엄마 금방 올게. 착하지.\"");
        sub.setText("보호자가 미리 녹음한 목소리를 들려주고 있어요");
        setBanner(null, 0);
        stopVoice();
        voice = MediaPlayer.create(this, R.raw.owner_voice);
        voice.start();
    }

    // 2단계: 화면을 어둡게, 숨쉬는 원 / 3단계: + 보호자 알림
    private void showNight(boolean notifyOwner) {
        stopVoice();
        root.setBackgroundColor(color(R.color.night_bg));
        tint(color(R.color.night_text), color(R.color.night_glow));
        face.setVisibility(View.INVISIBLE);
        stage.setText(notifyOwner ? "3단계 · 보호자 호출" : "2단계 · 진정 모드");
        headline.setText("천천히, 괜찮아");
        sub.setText("화면을 어둡게 하고 진정 음악을 틀었어요");
        setBanner(notifyOwner ? "보호자 폰으로 알림을 보냈어요 · \"" + DOG + "가 계속 짖고 있어요\"" : null, R.color.alert);
        if (breathing == null) {
            glow.setAlpha(0.8f);
            breathing = ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.7f, 1.1f);
            breathing.addUpdateListener(a -> glow.setScaleY(glow.getScaleX()));
            breathing.setDuration(4000);
            breathing.setRepeatMode(ValueAnimator.REVERSE);
            breathing.setRepeatCount(ValueAnimator.INFINITE);
            breathing.start();
        }
    }

    private void setBanner(String text, int bg) {
        banner.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text == null) return;
        banner.setText(text);
        banner.getBackground().mutate().setTint(color(bg));
    }

    private void tint(int main, int secondary) {
        headline.setTextColor(main);
        stage.setTextColor(secondary);
        listen.setTextColor(secondary);
        sub.setTextColor(secondary);
    }

    private void pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.2f);
        a.setDuration(900);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.start();
    }

    private void stopBreathing() {
        if (breathing != null) breathing.cancel();
        breathing = null;
        glow.setScaleX(1f);
        glow.setScaleY(1f);
    }

    private void stopVoice() {
        if (voice != null) voice.release();
        voice = null;
    }

    private int color(int id) {
        return ContextCompat.getColor(this, id);
    }

    private void show(TextView view, String text) {
        runOnUiThread(() -> view.setText(text));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(receiver);
        stopVoice();
        vehicle.release();
        super.onDestroy();
    }
}
