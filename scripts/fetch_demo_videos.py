#!/usr/bin/env python3
"""
Ролики для «Видео с рынка» — детейлинг и уход за авто (полировка микрофиброй, пена, химчистка салона…) с Mixkit
(бесплатная лицензия Mixkit Stock Video Free License, можно в коммерческих проектах, без указания автора).

  python3 scripts/fetch_demo_videos.py

Ролики (720p) и обложки — в scripts/demo_videos/stock/<id>.mp4 и <id>.jpg (в git не попадают). demo_seed.py
выкладывает их как товары с видео (VIDEO_POSTS). Обложку снимает video_poster.swift (кадр на 1,5 с).
"""
import os
import subprocess
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from demo_seed import VIDEO_POSTS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "demo_videos", "stock")
UA = "Mozilla/5.0 (KudaibergenDemoSeed; local development demo data)"


def main():
    os.makedirs(OUT, exist_ok=True)
    for _, stock_id, title, _, _ in VIDEO_POSTS:
        video = os.path.join(OUT, f"{stock_id}.mp4")
        if not os.path.exists(video):
            req = urllib.request.Request(f"https://assets.mixkit.co/videos/{stock_id}/{stock_id}-720.mp4", headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as resp, open(video, "wb") as fh:
                fh.write(resp.read())
        subprocess.run(["swift", os.path.join(HERE, "video_poster.swift"), video, os.path.join(OUT, f"{stock_id}.jpg")],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(f"{stock_id}: {title}", flush=True)


if __name__ == "__main__":
    main()
