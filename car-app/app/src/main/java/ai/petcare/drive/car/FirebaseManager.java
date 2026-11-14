package ai.petcare.drive.car;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 차량 앱 전용 Firebase 연동 관리자 (Car App Firebase Helper)
 */
public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;

    private FirebaseDatabase database;
    private FirebaseStorage storage;
    private DatabaseReference carRef;
    private String carId = "car_01";
    private boolean initialized = false;
    // DB는 로그인한 앱만 읽고 쓸 수 있다. 로그인 전에 붙인 리스너는 거부되므로 로그인 뒤에 붙인다
    private boolean signedIn = false;
    private final List<Runnable> afterSignIn = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface OnCommandListener {
        void onCommandReceived(String type, String voiceUrl, Map<String, Object> params);
    }

    public interface OnProfilesListener {
        void onProfilesUpdated(DataSnapshot profilesSnapshot);
    }

    private FirebaseManager() {}

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    // Firebase에 연결돼 이벤트를 올릴 수 있는지 (google-services.json이 있어야 true)
    public boolean isReady() {
        return initialized;
    }

    public void init(Context context, String carId) {
        if (initialized) return;
        this.carId = (carId != null && !carId.isEmpty()) ? carId : "car_01";
        try {
            // google-services.json이 없으면 null → 아래 getInstance에서 예외 → 오프라인으로 동작
            FirebaseApp.initializeApp(context);
            database = FirebaseDatabase.getInstance();
            storage = FirebaseStorage.getInstance();
            carRef = database.getReference("vehicles").child(this.carId);

            initialized = true;
            signIn();
            Log.d(TAG, "FirebaseManager initialized for vehicle: " + this.carId);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error (google-services.json check required)", e);
        }
    }

    // 익명 로그인. 막 켜져 네트워크가 없으면 5초 뒤 다시 시도한다
    private void signIn() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            onSignedIn();
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Firebase anonymous auth successful");
                onSignedIn();
            } else {
                Log.w(TAG, "Firebase auth failed, retry in 5s", task.getException());
                main.postDelayed(this::signIn, 5000);
            }
        });
    }

    private void onSignedIn() {
        signedIn = true;
        for (Runnable r : afterSignIn) r.run();
        afterSignIn.clear();
    }

    private void whenSignedIn(Runnable r) {
        if (signedIn) r.run();
        else afterSignIn.add(r);
    }

    /**
     * 차량 상태 업로드 (C-09, S-02)
     */
    public void uploadCarStatus(float tempC, int soc, float batteryWh, String state) {
        if (!initialized || carRef == null || !signedIn) return;
        Map<String, Object> status = new HashMap<>();
        if (!Float.isNaN(tempC)) status.put("temp", tempC);   // 모르면 올리지 않는다
        if (soc >= 0) status.put("battery", soc);
        if (batteryWh >= 0) status.put("batteryWh", batteryWh);
        status.put("state", state);                            // off · calm · calming
        status.put("updatedAt", ServerValue.TIMESTAMP);

        carRef.child("status").updateChildren(status)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload car status", e));
    }

    /** 에어컨 설정 온도를 올린다. 폰 온도 화면이 이 값으로 맞춘다 */
    public void reportTargetTemp(float tempC) {
        if (!initialized || carRef == null) return;
        carRef.child("status").child("targetTemp").setValue(tempC);
    }

    /**
     * 짖음 감지 및 진정 이벤트 발생 시 RTDB에 리포트 (C-05, S-03)
     */
    public void reportBarkEvent(int barkCount, boolean whine) {
        if (!initialized || carRef == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());

        Map<String, Object> event = new HashMap<>();
        event.put("time", timeStr);
        event.put("text", whine ? "낑낑거림 감지 · 진정" : "짖음 감지 · 진정");
        event.put("value", barkCount + "회");
        event.put("bark", true);
        event.put("whine", whine);
        event.put("timestamp", ServerValue.TIMESTAMP);

        carRef.child("events").push().setValue(event);
    }

    /**
     * 위험 이벤트 (C-10). Cloud Functions가 type=danger를 보고 긴급 푸시를 보내면 된다
     * reason: hot · cold · battery
     */
    public void reportDanger(String reason, float tempC, int soc) {
        if (!initialized || carRef == null) return;
        Map<String, Object> event = new HashMap<>();
        event.put("time", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        event.put("type", "danger");
        event.put("reason", reason);
        event.put("text", "battery".equals(reason) ? "배터리 부족" : "hot".equals(reason) ? "실내 온도 높음" : "실내 온도 낮음");
        if (!Float.isNaN(tempC)) event.put("temp", tempC);
        if (soc >= 0) event.put("battery", soc);
        event.put("bark", false);
        event.put("timestamp", ServerValue.TIMESTAMP);
        carRef.child("events").push().setValue(event);
    }

    /**
     * 진정 완료 이벤트 리포트
     */
    public void reportCalmCompleted(String durationStr) {
        if (!initialized || carRef == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());

        Map<String, Object> event = new HashMap<>();
        event.put("time", timeStr);
        event.put("text", "진정 완료");
        event.put("value", durationStr);
        event.put("bark", false);
        event.put("timestamp", ServerValue.TIMESTAMP);

        carRef.child("events").push().setValue(event);
    }

    /**
     * 보호자 원격 명령 수신 리스너 (C-13, S-05, P-07)
     */
    public void listenForCommands(final OnCommandListener listener) {
        if (!initialized || carRef == null) return;

        whenSignedIn(() -> carRef.child("commands").limitToLast(1).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean executed = child.child("executed").getValue(Boolean.class);
                    if (executed == null || !executed) {
                        String type = child.child("type").getValue(String.class);
                        String voiceUrl = child.child("voiceUrl").getValue(String.class);
                        Map<String, Object> params = (Map<String, Object>) child.child("params").getValue();

                        // 실행 완료 표시
                        child.getRef().child("executed").setValue(true);

                        if (listener != null && type != null) {
                            listener.onCommandReceived(type, voiceUrl, params);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Command listener cancelled", error.toException());
            }
        }));
    }

    /**
     * 폰 연결 확인 (C-02, S-01). 폰 앱 pairCar()가 vehicles/{carId}/info에 pairedAt을 쓰면 불린다
     */
    public void listenForPairing(final Runnable onPaired) {
        if (!initialized || carRef == null) return;
        whenSignedIn(() -> carRef.child("info").child("pairedAt").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) onPaired.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Pairing listener cancelled", error.toException());
            }
        }));
    }

    /**
     * 보호자 앱에서 등록된 반려견 프로필 변경 수신 (C-01 Sync)
     */
    public void listenForProfiles(final OnProfilesListener listener) {
        if (!initialized || carRef == null) return;

        whenSignedIn(() -> carRef.child("profiles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (listener != null) {
                    listener.onProfilesUpdated(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Profiles listener cancelled", error.toException());
            }
        }));
    }

    /**
     * Storage에서 음성 파일 다운로드 (C-07, S-04)
     */
    public void downloadVoiceFile(Context context, String voiceUrl, final FileCallback callback) {
        if (!initialized || storage == null || voiceUrl == null) {
            callback.onError(new Exception("Firebase not initialized or invalid voiceUrl"));
            return;
        }

        try {
            StorageReference ref = storage.getReferenceFromUrl(voiceUrl);
            File localFile = File.createTempFile("voice", ".mp3", context.getCacheDir());
            ref.getFile(localFile).addOnSuccessListener(taskSnapshot -> callback.onSuccess(localFile))
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public interface FileCallback {
        void onSuccess(File file);
        void onError(Exception e);
    }
}
