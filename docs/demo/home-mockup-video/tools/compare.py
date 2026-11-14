# 렌더한 프레임과 시안(ref/mockup.png)의 요소 위치를 같은 좌표(시안 픽셀)로 비교한다.
# 사용: python3 tools/compare.py <snapshot.png>
import subprocess, sys

W, H = 790, 1529  # 시안 속 폰 크기


def load(args):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", *args, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                         capture_output=True, check=True).stdout
    assert len(raw) == W * H * 3, len(raw)
    return raw


ref = load(["-i", "ref/mockup.png", "-vf", "crop=790:1529:118:5"])
mine = load(["-i", sys.argv[1], "-vf", "crop=932:1804:74:58,scale=790:1529:flags=lanczos"])


def px(d, x, y):
    i = (y * W + x) * 3
    return d[i], d[i + 1], d[i + 2]


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def bbox(d, r, cond):
    x0, y0, x1, y1 = (r[0] - 118, r[1] - 5, r[2] - 118, r[3] - 5)
    xs, ys = [], []
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            if cond(px(d, x, y)):
                xs.append(x)
                ys.append(y)
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


dark = lambda t: (lambda p: lum(p) < t)
blue = lambda p: p[2] > 150 and p[0] < 90

# 시안 좌표(전체 이미지 기준) 영역
R = {
    "time": ((150, 25, 300, 85), dark(110)),
    "status_icons": ((690, 25, 890, 85), dark(110)),
    "title": ((150, 95, 340, 195), dark(110)),
    "subtitle": ((150, 185, 460, 228), dark(190)),
    "bell": ((790, 105, 885, 195), dark(110)),
    "bell_dot": ((835, 100, 885, 150), blue),
    "lastconn": ((150, 585, 460, 622), dark(190)),
    "connected": ((720, 580, 890, 625), blue),
    "batt_icon": ((170, 680, 270, 780), blue),
    "num82": ((285, 670, 420, 740), dark(110)),
    "range": ((285, 740, 560, 778), dark(190)),
    "bar_fill": ((285, 775, 800, 800), blue),
    "batt_chev": ((800, 700, 860, 770), dark(150)),
    "t1_icon": ((170, 860, 240, 930), dark(110)),
    "t1_label": ((170, 935, 330, 970), dark(190)),
    "t1_val": ((170, 970, 370, 1025), dark(110)),
    "t2_icon": ((420, 860, 500, 930), dark(110)),
    "t2_label": ((420, 935, 560, 970), dark(190)),
    "t2_val": ((420, 970, 560, 1025), dark(110)),
    "t3_icon": ((640, 860, 710, 935), dark(110)),
    "t3_chev": ((810, 880, 870, 935), dark(150)),
    "t3_label": ((630, 935, 800, 970), dark(190)),
    "t3_val": ((630, 970, 870, 1025), dark(110)),
    "act_title": ((170, 1090, 320, 1135), dark(110)),
    "act_more": ((740, 1095, 870, 1135), dark(190)),
    "row1_time": ((170, 1160, 260, 1195), dark(190)),
    "row1_text": ((320, 1155, 700, 1200), dark(150)),
    "row2_text": ((320, 1215, 700, 1260), dark(150)),
    "row3_text": ((320, 1275, 700, 1320), dark(150)),
    "dot1": ((270, 1160, 310, 1195), blue),
    "handle": ((440, 1362, 590, 1385), lambda p: lum(p) < 205),
    "nav_home": ((180, 1405, 270, 1510), blue),
    "nav_ctrl": ((370, 1405, 470, 1510), dark(185)),
    "nav_loc": ((560, 1405, 660, 1510), dark(185)),
    "nav_more": ((750, 1405, 850, 1510), dark(185)),
}
print(f"{'element':13s} {'ref box':>22s} {'mine box':>22s}   dL  dT  dR  dB")
for k, (r, c) in R.items():
    a, b = bbox(ref, r, c), bbox(mine, r, c)
    if not a or not b:
        print(f"{k:13s} ref={a} mine={b}")
        continue
    dl, dt, dr, db = (b[i] - a[i] for i in range(4))
    flag = "  <" if max(map(abs, (dl, dt, dr, db))) >= 3 else ""
    print(f"{k:13s} {str(a):>22s} {str(b):>22s} {dl:4d}{dt:4d}{dr:4d}{db:4d}{flag}")
