package ai.petcare.drive.car;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.pleos.playground.vehicle.Vehicle;
import kotlin.Unit;

// 개발 환경 확인용 첫 화면: Vehicle SDK 연결 후 배터리 잔량을 읽어 표시
public class MainActivity extends AppCompatActivity {

    private Vehicle vehicle;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setTextSize(28);
        status.setPadding(48, 48, 48, 48);
        status.setText("Vehicle SDK 연결 중…");
        setContentView(status);

        vehicle = new Vehicle(this);
        vehicle.initialize();

        // SDK 콜백은 Kotlin 람다라 Java에서는 Unit.INSTANCE를 반환해야 함
        vehicle.getEvBattery().getEvBatteryLevel(
                level -> {
                    show("배터리 잔량: " + level);
                    return Unit.INSTANCE;
                },
                e -> {
                    show("배터리 조회 실패: " + e.getMessage());
                    return Unit.INSTANCE;
                });
    }

    private void show(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    @Override
    protected void onDestroy() {
        vehicle.release();
        super.onDestroy();
    }
}
