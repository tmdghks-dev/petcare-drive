#!/bin/zsh
# 보호자 앱 시연 영상 녹화 (폰 에뮬레이터 emulator-5556이 켜져 있고 앱이 설치된 상태에서 실행)
# 홈 → 짖음 기록 → 알림 화면 → 목소리 들려주기 → 뒤로 → 프로필 → 아이 추가
adb() { ~/Library/Android/sdk/platform-tools/adb -s emulator-5556 "$@"; }
# 화면에서 글자로 버튼을 찾아 가운데를 누른다
tap() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null
  local b=($(adb shell cat /sdcard/u.xml | tr '>' '\n' | grep "$1" | grep -oE 'bounds="[^"]*"' | head -1 | grep -oE '[0-9]+'))
  adb shell input tap $(( (b[1] + b[3]) / 2 )) $(( (b[2] + b[4]) / 2 ))
}
adb shell am force-stop ai.petcare.drive.petcare_phone
adb shell am start -W -n ai.petcare.drive.petcare_phone/.MainActivity >/dev/null
until adb shell uiautomator dump /sdcard/u.xml >/dev/null && adb shell cat /sdcard/u.xml | grep -q '편안해요'; do sleep 1; done

adb shell screenrecord --bit-rate 6000000 --time-limit 40 /sdcard/phone.mp4 & REC=$!
sleep 4; tap '짖음 감지'          # 알림 화면
sleep 4; tap '목소리 들려주기'
sleep 4; adb shell input keyevent BACK
sleep 3; tap 'content-desc="프로필'
sleep 3; tap '아이 추가'
sleep 4; adb shell pkill -INT screenrecord; wait $REC; sleep 2
adb pull /sdcard/phone.mp4 "${0:A:h}/phone-app-demo.mp4"
