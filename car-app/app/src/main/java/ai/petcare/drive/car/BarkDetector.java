package ai.petcare.drive.car;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier;
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult;
import com.google.mediapipe.tasks.audio.core.RunningMode;
import com.google.mediapipe.tasks.components.containers.AudioData;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.core.BaseOptions;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 차량 마이크 소리를 약 1초마다 YAMNet으로 분류해 짖음을 알린다. 소리는 저장하지 않는다.
final class BarkDetector {

    interface Listener {
        void onBark();   // 짖음이 들림 (메인 스레드에서 불림)
        void onQuiet();  // QUIET_MS 동안 조용함 (메인 스레드에서 불림)
    }

    static final String TAG = "BarkDetector";
    static final Set<String> DOG = Set.of("Dog", "Bark", "Yip", "Howl", "Bow-wow", "Growling", "Whimper (dog)");
    static final float THRESHOLD = 0.3f;  // 개 소리 점수가 이 이상이면 들린 것으로 본다. Logcat 보며 조정
    static final int SAMPLE_RATE = 16000; // YAMNet 입력: 1초에 16000개
    static final int WINDOW = 15600;      // YAMNet이 한 번에 듣는 길이: 0.975초 분량
    static final long PERIOD_MS = 975;    // 듣는 길이와 같게 해야 소리가 밀려 쌓이지 않는다
    static final long QUIET_MS = 60_000;  // 이만큼 조용하면 진정한 것으로 본다 (시험할 땐 10_000)

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioClassifier classifier;
    private AudioRecord record;
    private ScheduledExecutorService loop;
    private int hits;                     // 개 소리가 연속으로 들린 횟수
    private long lastDogAt, lastReportAt;

    BarkDetector(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    // 모델을 열고 녹음을 시작한 뒤, PERIOD_MS마다 classifyOnce()를 부른다
    void start() {
        if (loop != null) return; // 이미 듣는 중
        AudioClassifier.AudioClassifierOptions options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(5)
                .build();
        classifier = AudioClassifier.createFromOptions(context, options);
        // 모노(1채널), 16kHz. 버퍼는 0.975초 분량 × 4바이트 × 2 (공식 예제와 같음)
        record = classifier.createAudioRecord(1, SAMPLE_RATE, WINDOW * 4 * 2);
        record.startRecording();

        hits = 0;
        lastDogAt = System.currentTimeMillis();
        loop = Executors.newSingleThreadScheduledExecutor();
        loop.scheduleAtFixedRate(this::classifyOnce, PERIOD_MS, PERIOD_MS, TimeUnit.MILLISECONDS);
        Log.d(TAG, "듣기 시작");
    }

    // 최근 0.975초 소리를 모델에 넣어 개 소리 점수를 구하고, 짖음/조용함을 판단한다
    private void classifyOnce() {
        try {
            AudioData audio = AudioData.create(AudioData.AudioDataFormat.create(record.getFormat()), WINDOW);
            audio.load(record); // 마이크에 쌓인 소리를 가져온다
            AudioClassifierResult result = classifier.classify(audio);

            float dog = 0;
            StringBuilder top = new StringBuilder();
            for (Category c : result.classificationResults().get(0).classifications().get(0).categories()) {
                top.append(String.format(Locale.US, "%s %.2f, ", c.categoryName(), c.score()));
                if (DOG.contains(c.categoryName())) dog = Math.max(dog, c.score());
            }
            Log.d(TAG, String.format(Locale.US, "dog=%.2f | %s", dog, top));

            long now = System.currentTimeMillis();
            if (dog >= THRESHOLD) {
                hits++;
                lastDogAt = now;
            } else {
                hits = 0;
            }

            // 2번 연속(약 2초) 들려야 짖음으로 본다. 알린 뒤 3초는 다시 알리지 않는다
            if (hits >= 2 && now - lastReportAt > 3000) {
                lastReportAt = now;
                Log.d(TAG, "→ 짖음");
                main.post(listener::onBark);
            } else if (now - lastDogAt > QUIET_MS) {
                lastDogAt = now; // 한 번 알리고 다시 센다
                Log.d(TAG, "→ 조용함");
                main.post(listener::onQuiet);
            }
        } catch (RuntimeException e) {
            // 여기서 예외가 밖으로 나가면 반복이 소리 없이 멈춘다. 로그만 남기고 다음 차례에 다시 듣는다
            Log.e(TAG, "분류 실패", e);
        }
    }

    // 녹음과 모델을 닫는다
    void stop() {
        if (loop == null) return;
        loop.shutdownNow();
        try {
            loop.awaitTermination(1, TimeUnit.SECONDS); // 돌던 판단이 끝난 뒤 닫는다
        } catch (InterruptedException ignored) {
        }
        loop = null;
        record.stop();
        record.release();
        classifier.close();
        Log.d(TAG, "듣기 멈춤");
    }
}
