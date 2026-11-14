#!/bin/zsh
# 전체 기능 시연 녹화: Pleos 에뮬레이터(emulator-5554)와 폰 에뮬레이터(emulator-5556)를 동시에 녹화한다
# 연결 → 펫케어 시작 → 짖음(폰 알림) → 원격 온도 → 진정 → 낑낑거림 → 위험 경보 → 운전 중 휴식 알림
# 결과: docs/demo/raw/car.mp4, phone.mp4, timeline.txt(장면 시작 초)
A=~/Library/Android/sdk/platform-tools/adb
car() { $A -s emulator-5554 "$@"; }
ph()  { $A -s emulator-5556 "$@"; }
CARPKG=ai.petcare.drive.car; PHPKG=ai.petcare.drive.petcare_phone
DB=https://pleos-9aaf4-default-rtdb.firebaseio.com/vehicles
B="am broadcast -a $CARPKG"
OUT="${0:A:h}/raw"; mkdir -p "$OUT"
CAR=$(car shell "run-as $CARPKG --user 10 cat shared_prefs/petcare.xml" | grep -o 'car_[0-9a-f]*' | head -1)

# 준비: 차는 연결 전(QR 화면), 폰은 앱을 닫아 둔다
sh "${0:A:h}/unpair.sh" >/dev/null; sleep 4
ph shell am force-stop $PHPKG
ph shell "run-as $PHPKG sh -c 'mkdir -p shared_prefs && printf \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\n<map>\n<string name=\\\"flutter.carId\\\">$CAR</string>\n</map>\n\" > shared_prefs/FlutterSharedPreferences.xml'"
ph shell input keyevent KEYCODE_WAKEUP; ph shell input keyevent KEYCODE_HOME

# 서버 함수를 미리 깨워 둔다 (쉬고 있으면 첫 푸시가 몇 분 늦는다)
curl -s -X PUT -d '{"warm":{".sv":"timestamp"}}' "$DB/_warmup/events/w.json" >/dev/null; sleep 8

car shell screenrecord --size 1920x1080 --bit-rate 10000000 --time-limit 175 /sdcard/car.mp4 & R1=$!
ph shell screenrecord --bit-rate 8000000 --time-limit 175 /sdcard/phone.mp4 & R2=$!
T0=$(date +%s); mark() { echo "$(( $(date +%s) - T0 )) $1" | tee -a "$OUT/timeline.txt"; }
: > "$OUT/timeline.txt"; sleep 3

mark "연결: 폰에서 QR로 연결"
ph shell monkey -p $PHPKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3
curl -s -X PUT -d '{"pairedAt":{".sv":"timestamp"},"ownerUid":"demo"}' "$DB/$CAR/info.json" >/dev/null; sleep 6

mark "펫케어 시작"
car shell input tap 2160 1069; sleep 8

mark "짖음 감지 → 차가 달래고 폰에 알림"
car shell $B.BARK >/dev/null; sleep 2; car shell $B.BARK >/dev/null; sleep 12

mark "폰에서 원격 온도 조절"
ph shell input tap 405 2240; sleep 3; ph shell input tap 445 973; sleep 7
ph shell input tap 136 2240; sleep 3

mark "진정 완료"
car shell $B.CALM >/dev/null; sleep 9

mark "낑낑거림 감지"
car shell $B.WHINE >/dev/null; sleep 10; car shell $B.CALM >/dev/null; sleep 4

mark "위험 경보 (에뮬레이터 시뮬레이션)"
car shell "$B.DANGER --ef temp 29.5" >/dev/null; sleep 11
car shell $B.SAFE >/dev/null; sleep 6

mark "운전 중 휴식 알림 (에뮬레이터 시뮬레이션)"
car shell "$B.DRIVE --ei min 60" >/dev/null; sleep 9
car shell "$B.DRIVE --ei min 90" >/dev/null; sleep 7
car shell $B.REST >/dev/null; sleep 6
mark "끝"

car shell pkill -INT screenrecord; ph shell pkill -INT screenrecord; wait $R1 $R2; sleep 2
car pull /sdcard/car.mp4 "$OUT/car.mp4" >/dev/null
ph pull /sdcard/phone.mp4 "$OUT/phone.mp4" >/dev/null
echo "저장: $OUT"
