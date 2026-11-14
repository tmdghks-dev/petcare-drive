"""숏폼 v2: 장면 표 → index.html + compositions/s01..s13.html 생성.

사용: python3 build.py   (나레이션은 tts.py로 먼저 assets/vo2/에 만들어 둔다)
장면 내용·시간을 바꾸려면 SCENES만 고치고 다시 실행한다. 생성된 HTML은 Studio에서 그대로 편집할 수 있다.
"""
import json
from pathlib import Path

ROOT = Path(__file__).parent
VO = {v["id"]: v for v in json.loads((ROOT / "assets/vo2/vo.json").read_text())}

# 차 화면 원본 2560x1440 → 틀 안 972x547 / 폰 원본 1080x2400 → 틀 안 424x942
CAR = dict(w=972, h=547, sw=2560, sh=1440)
PHONE = dict(w=484, h=1075, sw=1080, sh=2400)
APP = (1.5, 1707, 640)       # 차 화면에서 왼쪽 계기판을 뺀 앱 영역
BANNER = (1.5, 1707, 560)    # 위쪽 알림 배너가 보이게 앱 영역을 조금 위로

# 장면: id, 나레이션, 길이, 나레이션 시작(장면 기준), 제목(강조 단어는 *표시*), 태그, 화면
SCENES = [
    dict(id="s01", vo="hook", dur=6.3, title="차에 혼자 두기|*걱정된* 적 있죠?", kind="dog"),
    dict(id="s02", vo="intro", dur=6.2, title="*펫케어 드라이브*", kind="car",
         clips=[("intro_idle", 0, 6.2, 0)], cams=[(0, None), (2.4, APP)]),
    dict(id="s03", vo="qr", dur=4.9, title="QR 한 번이면|*연결* 끝", kind="car", chip=("보호자 폰 연결됨", 3.0),
         clips=[("qr", 0, 2.6, 0.3), ("paired", 2.6, 2.3, 0.6)], cams=[(0, None), (0.3, (1.45, 1480, 720)), (2.6, BANNER)]),
    dict(id="s04", vo="drive", dur=4.9, title="운전 중엔|1시간마다 *쉬어 가요*", kind="car", tag="운전 중", sim=True,
         clips=[("rest", 0, 4.9, 0.8)], cams=[(0, APP), (0.7, BANNER)]),
    dict(id="s05", vo="start", dur=7.4, title="두고 내릴 아이 고르고|*펫케어 시작*", kind="car", tag="도착",
         clips=[("start", 0, 7.4, 0.4)], cams=[(0, None), (0.4, APP), (1.8, (1.6, 1900, 700)), (4.6, APP)]),
    dict(id="s06", vo="idle", dur=6.1, title="혼자 있어도|*안심*", kind="car",
         clips=[("idle", 0, 6.1, 0.5)], cams=[(0, APP), (2.6, (1.6, 1900, 760))]),
    dict(id="s07", vo="bark", dur=7.4, voat=1.1, title="알아듣고|바로 *달래요*", kind="car", bark=True,
         clips=[("calming", 1.0, 6.4, 0.6)], cams=[(1.0, APP), (3.6, (1.6, 1850, 760))]),
    dict(id="s08", vo="push", dur=4.7, title="앱이 꺼져 있어도|폰에 *알림*", kind="phone", sfx=[("notification", 0.6)],
         clips=[("p_push", 0, 4.7, 0.9)], cams=[(0, None), (1.1, (1.12, 540, 700))]),
    dict(id="s09", vo="temp", dur=5.6, title="폰에서 온도를 내리면|차 에어컨이 *바로*", kind="both",
         pclips=[("p_temp", 0, 2.9, 0.8)], pcams=[(0, None), (0.5, (1.0, 540, 1200))],
         clips=[("tempbanner", 2.9, 2.7, 0.4)], cams=[(2.9, BANNER)]),
    dict(id="s10", vo="calm", dur=5.6, title="15분 뒤|*진정 완료*", kind="car", timerjump=True,
         clips=[("calmsum", 0, 5.6, 0.6)], cams=[(0, APP), (1.4, (1.6, 1880, 640))]),
    dict(id="s11", vo="whine", dur=4.0, title="낑낑거림도|*따로* 알아들어요", kind="car",
         clips=[("whine", 0, 4.0, 0.8)], cams=[(0, APP), (0.8, (1.6, 1860, 660))]),
    dict(id="s12", vo="danger", dur=5.3, title="위험하면|*긴급 알림*", kind="car", danger=True, sim=True, sfx=[("notification", 0.5)],
         clips=[("danger", 0, 5.3, 0.7)], cams=[(0, APP), (1.6, (1.6, 1860, 700))]),
    dict(id="s13", vo="end", dur=8.6, kind="end"),
]

t = 0.0
for s in SCENES:
    s["start"] = round(t, 2)
    t += s["dur"]
TOTAL = round(t, 2)
START = {s["id"]: s["start"] for s in SCENES}

FONT = """
  @font-face { font-family: "Pretendard"; src: url("assets/fonts/Pretendard-Bold.woff2") format("woff2"); font-weight: 700; }
  @font-face { font-family: "Pretendard"; src: url("assets/fonts/Pretendard-ExtraBold.woff2") format("woff2"); font-weight: 800; }
"""
CSS = FONT + """
  #root { position: absolute; inset: 0; font-family: "Pretendard"; color: #202833; overflow: hidden; }
  .title { position: absolute; left: 80px; right: 80px; top: 366px; font-size: 84px; font-weight: 800; letter-spacing: -0.025em; line-height: 1.16; }
  .title .ln { display: block; }
  .title .w { display: inline-block; margin-right: 0.24em; }
  .accent { color: #087db5; }
  .red { color: #d93636; }
  .sub { position: absolute; left: 82px; right: 82px; top: 592px; font-size: 34px; font-weight: 700; line-height: 1.4; letter-spacing: -0.01em; }
  .sub .sw { color: #7d8792; }
  .tag { position: absolute; left: 80px; top: 312px; font-size: 34px; font-weight: 800; color: #087db5; }
  .sim { position: absolute; right: 80px; top: 688px; text-align: right; font-size: 26px; font-weight: 700; color: #5b6673; }
  .carframe { position: absolute; left: 40px; top: 740px; width: 1000px; padding: 14px; border-radius: 30px; background: #0d0f13;
    box-shadow: 0 36px 70px rgba(16, 24, 40, 0.22); }
  .phoneframe { position: absolute; left: 280px; top: 720px; width: 520px; padding: 18px; border-radius: 64px; background: #111;
    box-shadow: 0 36px 70px rgba(16, 24, 40, 0.22); }
  .scr { position: relative; overflow: hidden; background: #f1f4f6; }
  .carframe .scr { width: 972px; height: 547px; border-radius: 18px; }
  .phoneframe .scr { width: 484px; height: 1075px; border-radius: 48px; }
  .cam { position: absolute; left: 0; top: 0; width: 100%; height: 100%; transform-origin: 0 0; }
  .cam video { position: absolute; left: 0; top: 0; width: 100%; height: 100%; object-fit: fill; }
  .chip { position: absolute; left: 0; right: 0; top: 1296px; display: flex; justify-content: center; }
  .chip span { padding: 14px 30px; border-radius: 999px; background: #087db5; color: #fff; font-size: 32px; font-weight: 800; }
"""

JS_HELPERS = """
  function words(sel, at) {
    document.querySelectorAll(sel + " .w").forEach((el, i) => {
      tl.fromTo(el, { opacity: 0, filter: "blur(8px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.16, ease: "power4.out" }, at + i * 0.07);
    });
  }
  function karaoke(sel, at, times) {
    tl.fromTo(sel, { opacity: 0 }, { opacity: 1, duration: 0.2 }, at - 0.1);
    document.querySelectorAll(sel + " .sw").forEach((el, i) => {
      tl.fromTo(el, { color: "#7d8792" }, { color: "#202833", duration: 0.12, ease: "none" }, at + times[i]);
    });
  }
  // 화면 틀 안에서만 카메라를 민다: s배, 원본 좌표 (cx, cy)가 가운데 오도록, 가장자리는 넘지 않게
  function camTo(sel, at, s, cx, cy, d, W, H, SW, SH, first) {
    const k = W / SW, x = Math.min(0, Math.max(W - s * W, W / 2 - s * cx * k)), y = Math.min(0, Math.max(H - s * H, H / 2 - s * cy * k));
    if (first) tl.fromTo(sel, { scale: s, x: x, y: y }, { scale: s, x: x, y: y, duration: 0.01 }, at);
    else tl.to(sel, { scale: s, x: x, y: y, duration: d, ease: "power2.inOut" }, at);
  }
"""


def title_html(sid, title):
    lines = []
    for ln in title.split("|"):
        ws = []
        for w in ln.split(" "):
            acc = w.startswith("*") or w.endswith("*")
            cls = " accent" if (acc or getattr(title_html, "inacc", False)) else ""
            if w.startswith("*") and not w.endswith("*"):
                title_html.inacc = True
            if w.endswith("*"):
                title_html.inacc = False
            ws.append(f'<span class="w{cls}">{w.strip("*")}</span>')
        lines.append('<span class="ln">' + "".join(ws) + "</span>")
    return f'<div id="{sid}-title" class="title">' + "".join(lines) + "</div>"


def sub_html(sid, vo):
    v = VO[vo]
    spans = "".join(f'<span class="sw">{w["w"]}</span> ' for w in v["words"])
    return f'<div id="{sid}-sub" class="sub">{spans}</div>', [round(w["t"], 3) for w in v["words"]]


def device(sid, kind, clips, cams, frame):
    W, H, SW, SH = frame["w"], frame["h"], frame["sw"], frame["sh"]
    cls = "carframe" if frame is CAR else "phoneframe"
    pre = sid + ("-c" if frame is CAR else "-p")
    vids = "".join(
        f'<video id="{pre}-v{i}" class="clip" src="assets/clips2/{c}.mp4" data-start="{a}" data-duration="{d}" '
        f'data-media-start="{m}" muted playsinline></video>'
        for i, (c, a, d, m) in enumerate(clips))
    html = f'<div id="{pre}-frame" class="{cls}"><div class="scr"><div id="{pre}-cam" class="cam">{vids}</div></div></div>'
    js = []
    for i, (at, z) in enumerate(cams):
        s, cx, cy = z if z else (1, SW / 2, SH / 2)
        js.append(f'camTo("#{pre}-cam", {at}, {s}, {cx}, {cy}, 0.9, {W}, {H}, {SW}, {SH}, {"true" if i == 0 else "false"});')
    return html, js, pre


def scene_file(s):
    sid, kind, dur = s["id"], s["kind"], s["dur"]
    body, js, extra_css = [], [], ""
    voat = s.get("voat", 0.3)
    if kind == "end":
        extra_css = """
  #s13-l1, #s13-l2 { position: absolute; left: 80px; font-size: 104px; font-weight: 800; letter-spacing: -0.03em; }
  #s13-l1 { top: 700px; } #s13-l2 { top: 830px; color: #087db5; }
  #s13-l0 { position: absolute; left: 84px; top: 620px; font-size: 40px; font-weight: 700; color: #5b6673; }
  #s13-flood { position: absolute; width: 240px; height: 240px; left: 420px; top: 840px; border-radius: 50%; background: #087db5; }
  #s13-name { position: absolute; left: 80px; top: 680px; font-size: 128px; font-weight: 800; color: #fff; letter-spacing: -0.03em; }
  #s13-name .c { display: inline-block; }
  #s13-line { position: absolute; left: 84px; top: 850px; font-size: 52px; font-weight: 700; color: #e3f2fb; line-height: 1.35; }
  #s13-tag { position: absolute; left: 84px; top: 1080px; font-size: 44px; font-weight: 800; color: #fff; }
"""
        name = "".join(f'<span class="c">{ch if ch != " " else "&nbsp;"}</span>' for ch in "펫케어 드라이브")
        body += ['<div id="s13-l0">펫케어 드라이브는</div>', '<div id="s13-l1">운전 중에도,</div>', '<div id="s13-l2">혼자 기다릴 때도</div>',
                 '<div id="s13-flood"></div>', f'<div id="s13-name">{name}</div>',
                 '<div id="s13-line">반려견을 돌보는<br />차량용 앱</div>', '<div id="s13-tag">#HMG차량용앱개발공모전</div>']
        js += ['tl.fromTo("#s13-l0", { opacity: 0 }, { opacity: 1, duration: 0.3 }, 0.2);',
               'tl.fromTo("#s13-l1", { opacity: 0, filter: "blur(10px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2, ease: "power4.out" }, 1.0);',
               'tl.fromTo("#s13-l2", { opacity: 0, filter: "blur(10px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2, ease: "power4.out" }, 1.9);',
               'tl.fromTo("#s13-flood", { scale: 0 }, { scale: 12, duration: 0.7, ease: "power2.inOut" }, 3.7);',
               'tl.to("#s13-l0, #s13-l1, #s13-l2", { opacity: 0, duration: 0.2 }, 3.9);',
               'document.querySelectorAll("#s13-name .c").forEach((c, i) => tl.fromTo(c, { opacity: 0, filter: "blur(6px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2, ease: "power4.out" }, 4.3 + i * 0.07));',
               'tl.fromTo("#s13-line", { opacity: 0, filter: "blur(8px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2 }, 5.1);',
               'tl.fromTo("#s13-tag", { opacity: 0 }, { opacity: 1, duration: 0.3 }, 5.6);']
    else:
        if s.get("tag"):
            body.append(f'<div id="{sid}-tag" class="tag">{s["tag"]}</div>')
            js.append(f'tl.fromTo("#{sid}-tag", {{ opacity: 0 }}, {{ opacity: 1, duration: 0.25 }}, 0.05);')
        body.append(title_html(sid, s["title"]))
        js.append(f'words("#{sid}-title", 0.12);')
        sub, times = sub_html(sid, s["vo"])
        body.append(sub)
        js.append(f'karaoke("#{sid}-sub", {voat}, {json.dumps(times)});')
        if kind == "dog":
            extra_css = f"""
  #{sid}-halo {{ position: absolute; width: 640px; height: 640px; left: 220px; top: 700px; border-radius: 50%; background: #dff0fa; }}
  #{sid}-dog {{ position: absolute; width: 600px; left: 240px; top: 660px; }}
"""
            body += [f'<div id="{sid}-halo"></div>', f'<img id="{sid}-dog" src="assets/dali.png" alt="" />']
            js += [f'tl.fromTo("#{sid}-halo", {{ scale: 0.6, opacity: 0 }}, {{ scale: 1, opacity: 1, duration: 0.9, ease: "expo.out" }}, 0);',
                   f'tl.fromTo("#{sid}-dog", {{ scale: 1.1, y: 40 }}, {{ scale: 1, y: 0, duration: 1.0, ease: "expo.out" }}, 0);',
                   f'tl.to("#{sid}-dog, #{sid}-halo", {{ scale: 0.85, opacity: 0, duration: 0.4, ease: "power3.in" }}, {dur - 0.45});']
        if s.get("bark"):
            bars = "".join(f'<span class="bar" id="{sid}-b{i}"></span>' for i in range(21))
            extra_css += f"""
  #{sid}-wave {{ position: absolute; left: 90px; right: 90px; top: 840px; height: 400px; display: flex; align-items: center; justify-content: space-between; }}
  #{sid}-wave .bar {{ display: block; width: 26px; height: 400px; border-radius: 13px; background: #087db5; }}
  #{sid}-woof {{ position: absolute; left: 0; right: 0; top: 700px; text-align: center; font-size: 130px; font-weight: 800; }}
"""
            body += [f'<div id="{sid}-woof">멍! 멍!</div>', f'<div id="{sid}-wave">{bars}</div>']
            js += ['const amp = [0.35,0.6,0.9,0.5,1,0.7,0.4,0.85,0.95,0.55,1,0.6,0.9,0.45,0.8,1,0.5,0.7,0.95,0.4,0.6];',
                   f'document.querySelectorAll("#{sid}-wave .bar").forEach((b, i) => {{'
                   ' tl.fromTo(b, { scaleY: 0.06 }, { scaleY: amp[i], duration: 0.18, ease: "expo.out" }, 0.05 + i * 0.012);'
                   ' tl.to(b, { scaleY: amp[(i + 7) % 21] * 0.8, duration: 0.2, ease: "power2.inOut" }, 0.4 + i * 0.01);'
                   ' tl.to(b, { scaleY: amp[(i + 3) % 21], duration: 0.2, ease: "power2.inOut" }, 0.65 + i * 0.01); });',
                   f'tl.fromTo("#{sid}-woof", {{ opacity: 0, scale: 1.25, filter: "blur(10px)" }}, {{ opacity: 1, scale: 1, filter: "blur(0px)", duration: 0.2, ease: "expo.out" }}, 0.05);',
                   f'tl.to("#{sid}-woof", {{ opacity: 0, filter: "blur(10px)", duration: 0.2, ease: "power3.in" }}, 0.9);',
                   f'tl.to("#{sid}-wave", {{ scaleY: 0.02, opacity: 0, duration: 0.3, ease: "power3.in" }}, 0.85);']
        if s.get("danger"):
            extra_css += f"  #{sid}-ground {{ position: absolute; inset: 0; background: #fbe9e9; }}\n  #{sid}-title .accent {{ color: #d93636; }}\n"
            body.insert(0, f'<div id="{sid}-ground"></div>')
            js += [f'tl.fromTo("#{sid}-ground", {{ opacity: 0 }}, {{ opacity: 1, duration: 0.4, ease: "power2.inOut" }}, 0);',
                   f'tl.to("#{sid}-ground", {{ opacity: 0, duration: 0.35, ease: "power2.inOut" }}, {dur - 0.4});']
        if kind in ("car", "both"):
            h, j, pre = device(sid, kind, s["clips"], s["cams"], CAR)
            body.append(h); js += j
            enter = s["clips"][0][1]
            if s.get("bark"):
                js.append(f'tl.fromTo("#{pre}-frame", {{ scale: 0.3, opacity: 0 }}, {{ scale: 1, opacity: 1, duration: 0.7, ease: "expo.out" }}, {enter});')
            elif kind == "both":
                js.append(f'tl.fromTo("#{pre}-frame", {{ x: 1100 }}, {{ x: 0, duration: 0.6, ease: "expo.out" }}, {enter});')
            else:
                js.append(f'tl.fromTo("#{pre}-frame", {{ y: 70, opacity: 0, filter: "blur(10px)" }}, {{ y: 0, opacity: 1, filter: "blur(0px)", duration: 0.55, ease: "expo.out" }}, {enter});')
            js.append(f'tl.to("#{pre}-frame", {{ opacity: 0, y: -50, duration: 0.3, ease: "power3.in" }}, {dur - 0.35});')
        if kind in ("phone", "both"):
            clips = s.get("pclips", s.get("clips")); cams = s.get("pcams", s.get("cams"))
            h, j, pre = device(sid, kind, clips, cams, PHONE)
            body.append(h); js += j
            js.append(f'tl.fromTo("#{pre}-frame", {{ y: -900 }}, {{ y: 0, duration: 0.7, ease: "expo.out" }}, 0);')
            out = s["pclips"][0][1] + s["pclips"][0][2] - 0.4 if kind == "both" else dur - 0.4
            js.append(f'tl.to("#{pre}-frame", {{ x: {"-900" if kind == "both" else "0"}, opacity: {"1" if kind == "both" else "0"}, duration: 0.4, ease: "power3.in" }}, {out});')
        if s.get("sim"):
            body.append(f'<div id="{sid}-sim" class="sim">※ 에뮬레이터에서 시뮬레이션한 장면</div>')
        if s.get("chip"):
            txt, at = s["chip"]
            body.append(f'<div id="{sid}-chip" class="chip"><span id="{sid}-chipin">{txt}</span></div>')
            js.append(f'tl.fromTo("#{sid}-chipin", {{ scale: 0.6, opacity: 0 }}, {{ scale: 1, opacity: 1, duration: 0.35, ease: "back.out(1.6)" }}, {at});')
        js.append(f'tl.to("#{sid}-title, #{sid}-sub", {{ opacity: 0, filter: "blur(8px)", duration: 0.2 }}, {dur - 0.3});')
    return f"""<template>
<style>{CSS}{extra_css}</style>
<div id="root" data-composition-id="{sid}" data-width="1080" data-height="1920">
  {chr(10).join("  " + b for b in body)}
</div>
<script>
const tl = gsap.timeline({{ paused: true }});
{JS_HELPERS}
{chr(10).join("  " + x for x in js)}
window.__timelines["{sid}"] = tl;
</script>
</template>
"""


def index():
    hosts = "\n".join(
        f'      <div id="{s["id"]}" data-composition-id="{s["id"]}" data-composition-src="compositions/{s["id"]}.html" '
        f'data-start="{s["start"]}" data-duration="{s["dur"]}" data-track-index="1" data-track-kind="graphics" data-width="1080" data-height="1920"></div>'
        for s in SCENES)
    audio, k = [], 0
    for s in SCENES:
        v = VO[s["vo"]]
        a = round(s["start"] + s.get("voat", 0.3), 2)
        audio.append(f'      <audio id="vo-{s["id"]}" class="clip" src="assets/vo2/{s["vo"]}.mp3" data-start="{a}" '
                     f'data-duration="{min(v["dur"], s["dur"] - 0.1):.2f}" data-track-index="5" data-volume="1"></audio>')
    sfx = []
    whooshes = ["whoosh-short", "whoosh-cinematic"]
    for i, s in enumerate(SCENES[1:], 1):
        at = s["start"] + (1.0 if s.get("bark") else 0)
        sfx.append((whooshes[i % 2], round(max(0, at - 0.05), 2), 0.22))
        for name, off in s.get("sfx", []):
            sfx.append((name, round(s["start"] + off, 2), 0.32))
    sfx += [("pop", round(START["s03"] + 3.0, 2), 0.3), ("pop", round(START["s10"] + 0.3, 2), 0.3),
            ("pop", round(START["s07"] + 0.05, 2), 0.35), ("impact-bass-1", round(START["s13"] + 3.7, 2), 0.35)]
    for i, (name, at, vol) in enumerate(sfx):
        audio.append(f'      <audio id="sfx{i}" class="clip" src="assets/sfx/{name}.mp3" data-start="{at}" data-duration="1.8" '
                     f'data-track-index="{6 + i % 3}" data-volume="{vol}"></audio>')
    audio.append(f'      <audio id="music" class="clip" src="assets/music.wav" data-start="0" data-duration="{TOTAL}" '
                 f'data-track-index="9" data-volume="0.1" data-fade-out="2.5"></audio>')
    t_on, t_jump, t_off = START["s06"], START["s10"] + 0.3, START["s13"]
    return f"""<!doctype html>
<html lang="ko" data-resolution="portrait">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1080, height=1920" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
    <style>
{FONT}
      * {{ margin: 0; padding: 0; box-sizing: border-box; }}
      html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; background: #f1f4f6; }}
      #root {{ position: relative; width: 100%; height: 100%; font-family: "Pretendard"; }}
      #ground {{ position: absolute; inset: 0; background: #f1f4f6; }}
      #timer {{ position: absolute; left: 0; right: 0; top: 176px; display: flex; justify-content: flex-end; padding-right: 80px; z-index: 20; }}
      #timer-pill {{ display: flex; align-items: center; gap: 14px; padding: 12px 26px; border-radius: 999px; background: #fff;
        box-shadow: 0 10px 26px rgba(16, 24, 40, 0.1); }}
      #timer-dot {{ width: 16px; height: 16px; border-radius: 50%; background: #087db5; }}
      #timer-label {{ font-size: 28px; font-weight: 700; color: #5b6673; }}
      #timer-time {{ font-size: 32px; font-weight: 800; color: #202833; font-variant-numeric: tabular-nums; }}
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="{TOTAL}" data-width="1080" data-height="1920">
      <div id="ground"></div>
{hosts}
      <div id="timer" class="clip" data-start="{t_on}" data-duration="{round(t_off - t_on, 2)}" data-track-index="2">
        <div id="timer-pill"><span id="timer-dot"></span><span id="timer-label">혼자 남은 지</span><span id="timer-time">03:00</span></div>
      </div>
{chr(10).join(audio)}
    </div>
    <script>
      const tl = gsap.timeline({{ paused: true }});
      const clock = {{ s: 180 }};
      const show = () => {{
        const s = Math.floor(clock.s);
        document.querySelector("#timer-time").textContent = String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
      }};
      tl.fromTo("#timer-pill", {{ opacity: 0, x: 40 }}, {{ opacity: 1, x: 0, duration: 0.5, ease: "expo.out" }}, {t_on});
      tl.fromTo(clock, {{ s: 180 }}, {{ s: 470, duration: {round(t_jump - t_on, 2)}, ease: "none", onUpdate: show }}, {t_on});
      tl.to(clock, {{ s: 900, duration: 0.7, ease: "power3.out", onUpdate: show }}, {t_jump});
      tl.fromTo("#timer-time", {{ color: "#202833" }}, {{ color: "#087db5", duration: 0.3 }}, {t_jump});
      tl.to("#timer-dot", {{ backgroundColor: "#d93636", duration: 0.2 }}, {START["s12"]});
      tl.to("#timer-pill", {{ opacity: 0, duration: 0.3, ease: "power3.in" }}, {round(t_off - 0.35, 2)});
      window.__timelines["main"] = tl;
    </script>
  </body>
</html>
"""


(ROOT / "compositions").mkdir(exist_ok=True)
for s in SCENES:
    (ROOT / f"compositions/{s['id']}.html").write_text(scene_file(s))
(ROOT / "index.html").write_text(index())
print("total", TOTAL, "s;", " ".join(f'{s["id"]}@{s["start"]}' for s in SCENES))
