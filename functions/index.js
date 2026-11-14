const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * 짖음 감지 이벤트 RTDB 트리거 → FCM 푸시 알림 전송 (P-05, S-03)
 */
exports.onBarkEventCreated = functions.database
  .ref("/vehicles/{carId}/events/{eventId}")
  .onCreate(async (snapshot, context) => {
    const event = snapshot.val();
    const carId = context.params.carId;

    if (!event || (!event.bark && (!event.text || !event.text.includes("짖음")))) {
      return null;
    }

    console.log(`Bark event detected for vehicle: ${carId}`);

    // 차량에 등록된 보호자 FCM 토큰 목록 조회
    const tokensSnap = await admin.database().ref(`/vehicles/${carId}/fcmTokens`).once("value");
    if (!tokensSnap.exists()) {
      console.log(`No FCM tokens found for vehicle: ${carId}`);
      return null;
    }

    const tokens = Object.keys(tokensSnap.val());
    if (tokens.length === 0) return null;

    const payload = {
      notification: {
        title: "🐾 반려견 짖음 감지",
        body: "차에서 짖음이 감지되어 세 가지 진정 동작을 시작했어요.",
      },
      data: {
        click_action: "FLUTTER_NOTIFICATION_CLICK",
        screen: "alert",
        carId: carId,
        time: event.time || "",
      },
    };

    try {
      const response = await admin.messaging().sendToDevice(tokens, payload);
      console.log(`Successfully sent ${response.successCount} notification messages.`);
    } catch (error) {
      console.error("Error sending FCM notification:", error);
    }
    return null;
  });

/**
 * 차량 온도 이상 경보 RTDB 트리거 → 긴급 푸시 알림 전송 (P-08)
 */
exports.onEmergencyAlert = functions.database
  .ref("/vehicles/{carId}/status")
  .onUpdate(async (change, context) => {
    const after = change.after.val();
    const carId = context.params.carId;

    if (!after || typeof after.temp !== "number") return null;

    // 실내 온도가 28도 이상이거나 15도 이하일 때 긴급 경보
    const isEmergency = after.temp >= 28.0 || after.temp <= 15.0;
    if (!isEmergency) return null;

    const tokensSnap = await admin.database().ref(`/vehicles/${carId}/fcmTokens`).once("value");
    if (!tokensSnap.exists()) return null;

    const tokens = Object.keys(tokensSnap.val());
    if (tokens.length === 0) return null;

    const payload = {
      notification: {
        title: "⚠️ 차량 실내 온도 위험 경보",
        body: `현재 실내 온도가 ${after.temp.toFixed(1)}℃ 입니다. 환기 및 에어컨을 확인하세요!`,
      },
      data: {
        click_action: "FLUTTER_NOTIFICATION_CLICK",
        screen: "home",
        carId: carId,
      },
    };

    try {
      await admin.messaging().sendToDevice(tokens, payload);
      console.log(`Emergency alert sent for ${carId}`);
    } catch (error) {
      console.error("Error sending emergency alert:", error);
    }
    return null;
  });
