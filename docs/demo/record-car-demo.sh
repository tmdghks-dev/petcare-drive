#!/bin/zsh
# 차량 앱 시연 영상 녹화 (Pleos 에뮬레이터가 켜져 있고 앱이 설치된 상태에서 실행)
# 짖음 3회 → 1·2·3단계 진정 → 진정 완료 순서로 화면을 바꾸며 녹화한다.
adb() { ~/Library/Android/sdk/platform-tools/adb -s emulator-5554 "$@"; }
B="am broadcast -a ai.petcare.drive.car"
adb shell am force-stop ai.petcare.drive.car
adb shell am start -n ai.petcare.drive.car/.MainActivity >/dev/null; sleep 4
adb shell screenrecord --size 1920x1080 --bit-rate 8000000 --time-limit 40 /sdcard/demo.mp4 & REC=$!
sleep 5;  adb shell $B.BARK >/dev/null   # 1단계: 보호자 목소리
sleep 8;  adb shell $B.BARK >/dev/null   # 2단계: 진정 모드
sleep 6;  adb shell $B.BARK >/dev/null   # 3단계: 보호자 알림
sleep 7;  adb shell $B.CALM >/dev/null   # 진정 완료
sleep 7;  adb shell pkill -INT screenrecord; wait $REC; sleep 2
adb pull /sdcard/demo.mp4 "${0:A:h}/car-app-demo.mp4"
