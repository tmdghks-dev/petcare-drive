#!/bin/sh
# 촬영용: Pleos 에뮬레이터의 보호자 폰 연결을 끊고 QR 화면으로 되돌린다
# 사용: sh docs/demo/unpair.sh
E=emulator-5554; P=ai.petcare.drive.car
DB=https://pleos-9aaf4-default-rtdb.firebaseio.com/vehicles
CAR=$(adb -s $E shell "run-as $P --user 10 cat shared_prefs/petcare.xml" | grep -o 'car_[0-9a-f]*' | head -1)
adb -s $E shell am force-stop $P
adb -s $E shell "run-as $P --user 10 sh -c 'sed -i \"/name=\\\"paired\\\"/d\" shared_prefs/petcare.xml'"
curl -s -X DELETE "$DB/$CAR/info.json" >/dev/null
curl -s -X DELETE "$DB/$CAR/fcmTokens.json" >/dev/null
adb -s $E shell am start --user 10 -n $P/.MainActivity >/dev/null
echo "연결 끊음: $CAR (차 화면에 QR이 다시 떠요)"
