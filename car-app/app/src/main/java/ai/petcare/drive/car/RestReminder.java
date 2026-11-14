package ai.petcare.drive.car;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.media.MediaPlayer;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;

// 운전 중 휴식 알림: 반려견과 오래 달리면 쉬어 가자고 말로 알려 준다
// 기어가 P가 아니면 주행으로 보고 시간을 잰다. P로 5분 넘게 있으면 쉰 것으로 보고 처음부터 다시 잰다
// 주행 중이라 누를 버튼은 없다. 음성 + 저절로 사라지는 짧은 알림만
// TODO: 같이 탄 아이는 ①에서 고른 아이로 본다. "오늘 함께 타요" 확인 화면은 필요해지면 추가
// TODO: Activity가 살아 있는 동안만 동작. 앱이 꺼져도 돌아야 하면 Foreground Service로 옮긴다
class RestReminder {
    private static final String TAG = "RestReminder";
    static final long INTERVAL_MS = 60 * 60_000; // 첫 알림: 1시간 주행
    static final long REPEAT_MS = 30 * 60_000;   // 안 쉬면 30분 뒤 한 번 더. 그 뒤로는 조용히
    static final long REST_MS = 5 * 60_000;      // P로 이만큼 있으면 쉰 것
    private static final long TICK_MS = 30_000;

    private final Context ctx;
    private final Vehicle vehicle;
    private final Supplier<String> names;
    private final Consumer<String> banner; // 화면 위 짧은 알림 (Pleos는 Toast를 띄우지 않는다)
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;

    private boolean parked = true;
    private long drivingSince; // 0이면 잴 주행이 없음
    private long parkedAt;
    private int alerts;

    private final GearSelectionListener gear = new GearSelectionListener() {
        @Override
        public void onSelected(VehicleGear g) {
            ui.post(() -> onGear(g == VehicleGear.GEAR_PARK));
        }

        @Override
        public void onFailed(Exception e) {
            Log.w(TAG, "기어 상태를 받을 수 없어요 (권한 확인)", e); // 시연 신호 DRIVE/REST로는 계속 동작
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
            ui.postDelayed(this, TICK_MS);
        }
    };

    RestReminder(Context ctx, Vehicle vehicle, Supplier<String> names, Consumer<String> banner) {
        this.ctx = ctx;
        this.vehicle = vehicle;
        this.names = names;
        this.banner = banner;
    }

    void start() {
        tts = new TextToSpeech(ctx, status -> {
            Log.d(TAG, "음성 엔진 " + (status == TextToSpeech.SUCCESS ? "준비됨" : "못 씀 " + status));
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) tts.setLanguage(Locale.KOREAN);
        }, "ai.pleos.playground.caas"); // Pleos 음성 엔진. 기본 엔진 설정이 비어 있어 직접 지정
        try {
            vehicle.getGear().registerGearSelection(gear);
        } catch (Exception e) {
            Log.w(TAG, "기어 상태를 받을 수 없어요 (권한 확인)", e); // 시연 신호 DRIVE/PARK로는 계속 동작
        }
        ui.post(tick);
    }

    void stop() {
        ui.removeCallbacksAndMessages(null);
        try {
            vehicle.getGear().unregisterGearSelection(gear);
        } catch (Exception ignored) {
        }
        if (tts != null) tts.shutdown();
    }

    void onGear(boolean park) {
        long now = System.currentTimeMillis();
        if (park && !parked) parkedAt = now;
        if (!park && drivingSince == 0) drivingSince = now;
        parked = park;
        Log.d(TAG, (park ? "P" : "주행") + " · 주행 " + (drivingSince == 0 ? 0 : (now - drivingSince) / 60_000) + "분");
        check();
    }

    // 시연용: 이미 min분 달린 것으로 (DRIVE 60 → 첫 알림, 이어서 DRIVE 90 → 두 번째) / 이미 5분 쉰 것으로
    void demoDrive(int min) {
        parked = false;
        drivingSince = System.currentTimeMillis() - min * 60_000L;
        check();
    }

    void demoRest() {
        parked = true;
        parkedAt = System.currentTimeMillis() - REST_MS;
        check();
    }

    private void check() {
        if (drivingSince == 0) return;
        long now = System.currentTimeMillis();
        if (parked) {
            if (now - parkedAt < REST_MS) return;
            if (alerts > 0) show(Dog.josa(names.get(), "이", "가") + " 잘 쉬었어요. 다시 출발해요!", 0);
            drivingSince = 0;
            alerts = 0;
            return;
        }
        if (alerts >= 2 || now - drivingSince < INTERVAL_MS + alerts * REPEAT_MS) return;
        alerts++;
        String name = names.get();
        long hours = Math.max(1, (now - drivingSince) / 3_600_000);
        show(alerts == 1
                ? Dog.josa(name, "과", "와") + " " + hours + "시간째 달리고 있어요. 다음 휴게소에서 물 한 모금, 잠깐 산책 어때요?"
                : Dog.josa(name, "이", "가") + " 오래 참고 있어요. 가까운 곳에서 잠깐 쉬어 가요.",
                alerts == 1 ? R.raw.rest_1 : R.raw.rest_2);
    }

    // 내비 등 다른 앱이 앞에 있어도 위에 뜨도록 시스템 알림(heads-up)으로도 보낸다. 10초 뒤 저절로 사라짐
    private void notifyCar(String text) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("rest", "휴식 알림", NotificationManager.IMPORTANCE_HIGH));
        nm.notify(1, new Notification.Builder(ctx, "rest")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("펫케어")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setTimeoutAfter(10_000)
                .setAutoCancel(true)
                .build());
    }

    // clip: 음성 엔진을 못 쓸 때 대신 틀 녹음. 0이면 말하지 않는다
    // TODO: 녹음은 "달이·1시간" 고정 문구(시연용). Pleos 음성 SDK를 붙이면 녹음 없이 tts만 쓰면 됨
    private void show(String text, int clip) {
        Log.d(TAG, text);
        banner.accept(text);
        notifyCar(text);
        if (clip == 0) return;
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rest");
            return;
        }
        MediaPlayer mp = MediaPlayer.create(ctx, clip);
        mp.setOnCompletionListener(MediaPlayer::release);
        mp.start();
    }
}
