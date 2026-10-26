# 펫케어 드라이브 (PetCare Drive)

반려견이 차 안에 혼자 있을 때 짖음을 감지해 차량이 스스로 진정시키고, 보호자는 스마트폰으로 상태를 확인하고 대응하는 차량용 반려견 케어 플랫폼.

현대자동차그룹 차량용 앱 개발 공모전 출품작.

## 구성

| 폴더 | 내용 |
|---|---|
| `car-app/` | 차량 앱 (Pleos Connect, Android Java) |
| `phone-app/` | 보호자 앱 (Flutter, Android·iOS) |
| `apk/` | 제출용 APK (`팀명_PetCareDrive_release.apk`) |
| `docs/` | 기획서, 설계도 |

## 실행 방법

### 차량 앱
1. Android Studio에서 `car-app/` 열기
2. Device Manager에서 `Pleos_Connect_v2.1.0` AVD 실행
3. Run 또는 `./gradlew installDebug`

테스트 환경: Pleos Connect Emulator 2.1.0 (Android 14), JDK 21 (Android Studio > Settings > Gradle JDK를 21로 지정), Gradle 8.10.2, AGP 8.8.2, Vehicle SDK 2.3.2

### 보호자 앱
```bash
cd phone-app
flutter run
```

### 참고: Vehicle SDK 권한
에뮬레이터 확인 결과 배터리 조회에는 `android.car.permission.CAR_ENERGY`가 아니라 `pleos.car.permission.CAR_ENERGY`가 필요하다. 다른 도메인도 `pleos.car.permission.*` 쪽을 먼저 확인할 것.
