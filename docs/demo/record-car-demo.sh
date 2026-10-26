#!/bin/zsh
# 차량 앱 시연 영상 녹화 (Pleos 에뮬레이터가 켜져 있고 앱이 설치된 상태에서 실행)
# 아이 선택 → 출발 → 펫케어 대기 → 짖음(알림·조명·음악 동시) → 진정 완료 순서로 녹화한다.
adb() { ~/Library/Android/sdk/platform-tools/adb -s emulator-5554 "$@"; }
B="am broadcast -a ai.petcare.drive.car"
adb shell am force-stop ai.petcare.drive.car
adb shell am start -n ai.petcare.drive.car/.MainActivity >/dev/null; sleep 4

# "이 아이와 출발하기" 버튼 위치를 화면에서 찾아 누른다
bounds=$(adb shell uiautomator dump /dev/tty | grep -o 'id/btnStart"[^>]*bounds="[^"]*"' | grep -oE '[0-9]+' | tail -4)
set -- ${=bounds}

adb shell screenrecord --size 1920x1080 --bit-rate 8000000 --time-limit 45 /sdcard/demo.mp4 & REC=$!
sleep 4;  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))   # 출발 → 대기 화면
sleep 7;  adb shell $B.BARK >/dev/null   # 짖음 감지 → 알림·조명·음악 동시 실행
sleep 4;  adb shell $B.BARK >/dev/null   # 계속 짖음 (횟수만 올라감)
sleep 10; adb shell $B.CALM >/dev/null   # 진정 완료
sleep 7;  adb shell pkill -INT screenrecord; wait $REC; sleep 2
adb pull /sdcard/demo.mp4 "${0:A:h}/car-app-demo.mp4"
