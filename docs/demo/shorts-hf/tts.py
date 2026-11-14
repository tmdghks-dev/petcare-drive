import asyncio, json, subprocess, edge_tts
VOICE, RATE = "ko-KR-SunHiNeural", "+5%"
async def one(k, t):
    words = []
    com = edge_tts.Communicate(t, VOICE, rate=RATE, boundary="WordBoundary")
    with open(f"assets/vo2/{k}.mp3", "wb") as f:
        async for ch in com.stream():
            if ch["type"] == "audio": f.write(ch["data"])
            elif ch["type"] == "WordBoundary": words.append({"t": ch["offset"]/1e7, "d": ch["duration"]/1e7, "w": ch["text"]})
    d = float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",f"assets/vo2/{k}.mp3"]))
    return k, t, d, words
async def main():
    rows = [l.split("|",1) for l in open("script-v2.txt").read().strip().split("\n")]
    out = [await one(k,t) for k,t in rows]
    json.dump([{"id":k,"text":t,"dur":d,"words":w} for k,t,d,w in out], open("assets/vo2/vo.json","w"), ensure_ascii=False, indent=1)
    for k,t,d,w in out: print(f"{k:7s} {d:5.2f}s words={len(w)}")
asyncio.run(main())
