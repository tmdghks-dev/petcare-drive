// 푸시 문구 확인: node test.js
const assert = require("node:assert");
const { toMessage } = require("./index.js");

assert.equal(toMessage({ bark: true, value: "2회" }).title, "달이가 짖고 있어요");
assert.equal(toMessage({ bark: true, whine: true, value: "1회" }).title, "달이가 낑낑거려요");
assert.equal(toMessage({ type: "danger", text: "실내 온도 높음", temp: 29.5 }).body, "실내 29.5°C · 차를 확인해 주세요");
assert.equal(toMessage({ text: "진정 완료", value: "1분" }).kind, "calm");
assert.equal(toMessage({ text: "기타" }), null);
console.log("ok");
