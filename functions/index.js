const { onValueCreated } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// 차 앱이 남긴 기록(짖음·낑낑·진정·위험)을 그 차에 연결된 보호자 폰으로 푸시한다 (P-05, S-03, C-10)
// 위험 판단은 차 앱이 반려견 적정 온도로 하고 type=danger 기록을 남긴다. 여기서는 전달만 한다
exports.pushCarEvent = onValueCreated(
  { ref: "/vehicles/{carId}/events/{eventId}", region: "us-central1" },
  async (event) => {
    const e = event.data.val();
    const carId = event.params.carId;
    const msg = toMessage(e);
    if (!msg) return;

    const tokensRef = getDatabase().ref(`/vehicles/${carId}/fcmTokens`);
    const tokens = Object.keys((await tokensRef.get()).val() || {});
    if (tokens.length === 0) return;

    const res = await getMessaging().sendEachForMulticast({
      tokens,
      notification: { title: msg.title, body: msg.body },
      data: { kind: msg.kind, time: String(e.time || "") },
      android: {
        priority: "high",
        notification: { channelId: msg.kind === "danger" ? "danger" : "bark", tag: msg.kind },
      },
      apns: { payload: { aps: { sound: "default" } } },
    });

    // 앱을 지웠거나 만료된 토큰은 지운다
    const dead = {};
    res.responses.forEach((r, i) => {
      const code = r.error && r.error.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        dead[tokens[i]] = null;
      }
    });
    if (Object.keys(dead).length) await tokensRef.update(dead);
    console.log(`${carId}: ${msg.kind} → ${res.successCount}/${tokens.length}`);
  },
);

// 폰 앱 car_alerts.dart와 같은 문구
function toMessage(e) {
  if (!e) return null;
  const value = e.value || "";
  if (e.type === "danger") {
    const temp = typeof e.temp === "number" ? `실내 ${e.temp.toFixed(1)}°C · ` : "";
    return { kind: "danger", title: `위험: ${e.text || ""}`, body: `${temp}차를 확인해 주세요` };
  }
  if (e.bark === true) {
    const whine = e.whine === true;
    return {
      kind: "bark",
      title: whine ? "달이가 낑낑거려요" : "달이가 짖고 있어요",
      body: `차에서 조명·음악·목소리로 달래는 중이에요 · ${whine ? "낑낑" : "짖음"} ${value}`,
    };
  }
  if (e.text === "진정 완료") {
    return { kind: "calm", title: "달이가 진정했어요", body: `달래는 데 ${value} 걸렸어요` };
  }
  return null;
}

module.exports.toMessage = toMessage;
