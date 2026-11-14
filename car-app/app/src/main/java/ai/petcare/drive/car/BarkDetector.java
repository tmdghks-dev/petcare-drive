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
        void onBark(boolean whine);   // 짖음(false)·낑낑거림(true)이 들림 (메인 스레드에서 불림)
        void onQuiet();  // QUIET_MS 동안 조용함 (메인 스레드에서 불림)
    }

    static final String TAG = "BarkDetector";
    static final Set<String> DOG = Set.of("Dog", "Bark", "Yip", "Howl", "Bow-wow", "Growling", "Canidae, dogs, wolves");
    static final float THRESHOLD = 0.3f;  // 개 소리 점수가 이 이상이면 들린 것으로 본다. Logcat 보며 조정
    // 낑낑거림은 짖음보다 점수가 낮게 나와 기준을 따로 둔다
    // ponytail: 0.2는 몇 번 들어 보고 정한 값. 오탐·미탐이 보이면 Logcat의 whine= 값을 보고 조정
    static final float WHINE_THRESHOLD = 0.2f;
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
    private boolean lastWhine;

    BarkDetector(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    // 시연 신호(adb BARK·WHINE)로 짖음을 만들었을 때 마이크도 '방금 들렸다'로 맞춘다. 안 그러면 바로 조용함으로 판단
    void markHeard() {
        lastDogAt = System.currentTimeMillis();
    }

    // 모델을 열고 녹음을 시작한 뒤, PERIOD_MS마다 classifyOnce()를 부른다
    void start() {
        if (loop != null) return; // 이미 듣는 중
        AudioClassifier.AudioClassifierOptions options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(10) // 낑낑 소리는 점수가 낮아 5위 밖으로 밀리기도 한다
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

            float dog = 0, whineDog = 0, whimper = 0, pets = 0;
            StringBuilder top = new StringBuilder();
            for (Category c : result.classificationResults().get(0).classifications().get(0).categories()) {
                String n = c.categoryName();
                top.append(String.format(Locale.US, "%s %.2f, ", n, c.score()));
                if (DOG.contains(n)) dog = Math.max(dog, c.score());
                else if ("Whimper (dog)".equals(n)) whineDog = c.score();
                else if ("Whimper".equals(n)) whimper = c.score();
                else if ("Domestic animals, pets".equals(n)) pets = c.score();
            }
            // 사람 '훌쩍임'(Whimper)은 개·반려동물 소리가 같이 들릴 때만 낑낑거림으로 센다
            float whine = Math.max(whineDog, dog >= 0.05f || pets >= 0.1f ? whimper : 0);
            Log.d(TAG, String.format(Locale.US, "dog=%.2f whine=%.2f | %s", dog, whine, top));

            long now = System.currentTimeMillis();
            boolean heard = dog >= THRESHOLD || whine >= WHINE_THRESHOLD;
            if (heard) lastWhine = whine >= WHINE_THRESHOLD && whine > dog;
            if (heard) {
                hits++;
                lastDogAt = now;
            } else {
                hits = 0;
            }

            // 2번 연속(약 2초) 들려야 짖음으로 본다. 알린 뒤 3초는 다시 알리지 않는다
            if (hits >= 2 && now - lastReportAt > 3000) {
                lastReportAt = now;
                boolean w = lastWhine;
                Log.d(TAG, w ? "→ 낑낑거림" : "→ 짖음");
                main.post(() -> listener.onBark(w));
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
