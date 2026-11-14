#!/bin/zsh
# 숏폼 v2용 녹화: 차(2560x1440 원본)와 폰을 동시에, 기능을 모두 한 번에
# QR 연결 → 운전 중 휴식 안내 → 음악 켜고 펫케어 시작 → 대기 → 짖음·폰 알림 → 원격 온도 → 진정 요약 → 낑낑 → 위험
# 결과: docs/demo/raw2/car.mp4, phone.mp4, timeline.txt
A=~/Library/Android/sdk/platform-tools/adb
car() { $A -s emulator-5554 "$@"; }
ph()  { $A -s emulator-5556 "$@"; }
CARPKG=ai.petcare.drive.car; PHPKG=ai.petcare.drive.petcare_phone
DB=https://pleos-9aaf4-default-rtdb.firebaseio.com/vehicles
B="am broadcast -a $CARPKG"
OUT="${0:A:h}/raw2"; mkdir -p "$OUT"
CAR=$(car shell "run-as $CARPKG --user 10 cat shared_prefs/petcare.xml" | grep -o 'car_[0-9a-f]*' | head -1)

# 준비: 차는 QR 화면, '기다리는 동안 음악'은 꺼진 상태, 폰은 앱을 닫아 둔다
car shell "run-as $CARPKG --user 10 sh -c 'sed -i \"/idleMusic/d\" shared_prefs/petcare.xml'"
sh "${0:A:h}/unpair.sh" >/dev/null; sleep 4
ph shell am force-stop $PHPKG
ph shell "run-as $PHPKG sh -c 'mkdir -p shared_prefs && printf \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\n<map>\n<string name=\\\"flutter.carId\\\">$CAR</string>\n</map>\n\" > shared_prefs/FlutterSharedPreferences.xml'"
ph shell input keyevent KEYCODE_WAKEUP; ph shell input keyevent KEYCODE_HOME
curl -s -X PUT -d '{"warm":{".sv":"timestamp"}}' "$DB/_warmup/events/w.json" >/dev/null; sleep 6

car shell screenrecord --size 2560x1440 --bit-rate 20000000 --time-limit 175 /sdcard/car.mp4 & R1=$!
ph shell screenrecord --bit-rate 10000000 --time-limit 175 /sdcard/phone.mp4 & R2=$!
T0=$(date +%s); : > "$OUT/timeline.txt"
mark() { echo "$(( $(date +%s) - T0 )) $1" | tee -a "$OUT/timeline.txt"; }
sleep 3

mark qr;      sleep 4
ph shell monkey -p $PHPKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3
curl -s -X PUT -d '{"pairedAt":{".sv":"timestamp"},"ownerUid":"demo"}' "$DB/$CAR/info.json" >/dev/null; sleep 6
mark drive;   car shell "$B.DRIVE --ei min 60" >/dev/null; sleep 9
car shell $B.REST >/dev/null; sleep 2
mark start;   car shell input tap 2387 685; sleep 3          # 기다리는 동안 음악 켜기
car shell input tap 2160 1069; sleep 1                        # 펫케어 시작
mark idle;    sleep 8
mark bark;    car shell $B.BARK >/dev/null; sleep 2; car shell $B.BARK >/dev/null; sleep 9
mark temp;    ph shell input tap 405 2240; sleep 3; ph shell input tap 445 973; sleep 6
ph shell input tap 136 2240; sleep 2
mark calm;    car shell $B.CALM >/dev/null; sleep 9
mark whine;   car shell $B.WHINE >/dev/null; sleep 9; car shell $B.CALM >/dev/null; sleep 3
mark danger;  car shell "$B.DANGER --ef temp 29.5" >/dev/null; sleep 10
car shell $B.SAFE >/dev/null; sleep 4
mark end

car shell pkill -INT screenrecord; ph shell pkill -INT screenrecord; wait $R1 $R2; sleep 2
car pull /sdcard/car.mp4 "$OUT/car.mp4" >/dev/null
ph pull /sdcard/phone.mp4 "$OUT/phone.mp4" >/dev/null
echo "저장: $OUT"
