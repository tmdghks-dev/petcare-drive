package ai.petcare.drive.car;

import android.content.Context;
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
import java.util.HashMap;
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

    public void init(Context context, String carId) {
        if (initialized) return;
        this.carId = (carId != null && !carId.isEmpty()) ? carId : "car_01";
        try {
            FirebaseApp.initializeApp(context);
            database = FirebaseDatabase.getInstance();
            storage = FirebaseStorage.getInstance();
            carRef = database.getReference("vehicles").child(this.carId);

            // 익명 로그인을 통해 Firebase 접근 권한 확보
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                auth.signInAnonymously().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase anonymous auth successful");
                    } else {
                        Log.w(TAG, "Firebase auth failed", task.getException());
                    }
                });
            }

            initialized = true;
            Log.d(TAG, "FirebaseManager initialized for vehicle: " + this.carId);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization error (google-services.json check required)", e);
        }
    }

    /**
     * 차량 상태 업로드 (C-09, S-02)
     */
    public void uploadCarStatus(double temp, int battery, boolean locked, String place, int careHours) {
        if (!initialized || carRef == null) return;
        Map<String, Object> status = new HashMap<>();
        status.put("temp", temp);
        status.put("battery", battery);
        status.put("locked", locked);
        status.put("place", place);
        status.put("careHours", careHours);
        status.put("updatedAt", ServerValue.TIMESTAMP);

        carRef.child("status").setValue(status)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload car status", e));
    }

    /**
     * 짖음 감지 및 진정 이벤트 발생 시 RTDB에 리포트 (C-05, S-03)
     */
    public void reportBarkEvent(int barkCount, String valueNote) {
        if (!initialized || carRef == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date());

        Map<String, Object> event = new HashMap<>();
        event.put("time", timeStr);
        event.put("text", "짖음 감지 · 진정");
        event.put("value", barkCount > 0 ? barkCount + "회" : valueNote);
        event.put("bark", true);
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

        carRef.child("commands").limitToLast(1).addValueEventListener(new ValueEventListener() {
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
        });
    }

    /**
     * 보호자 앱에서 등록된 반려견 프로필 변경 수신 (C-01 Sync)
     */
    public void listenForProfiles(final OnProfilesListener listener) {
        if (!initialized || carRef == null) return;

        carRef.child("profiles").addValueEventListener(new ValueEventListener() {
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
        });
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
