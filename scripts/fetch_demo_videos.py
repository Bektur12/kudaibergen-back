#!/usr/bin/env python3
"""
Фото для демо-видео «Видео с рынка» — тюнинг и красота авто (салон, диски, полировка, подсветка…), с Wikimedia
Commons, только свободные лицензии. Из них make_demo_video.swift собирает 12-секундные ролики, demo_seed.py
выкладывает их как товары с видео.

  python3 scripts/fetch_demo_videos.py --search   # кандидаты для ручного отбора
  python3 scripts/fetch_demo_videos.py            # скачать отобранные из scripts/demo_videos.json

Фото — в scripts/demo_videos/<тема>/<n>.jpg (в git не попадают), авторы и лицензии — scripts/demo_videos/ATTRIBUTION.md.
"""
import io
import json
import os
import sys

from PIL import Image, ImageOps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fetch_demo_photos as commons  # noqa: E402

# ролик вертикальный 720×1280, а фото почти все горизонтальные — берём миниатюру побольше
commons.THUMB_WIDTH = 1920

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "demo_videos")
PER_TOPIC = 4

# тема (ключ для demo_seed.VIDEO_POSTS) → категории Commons и слова в имени файла (для --search)
TOPICS = {
    "polish": (["Car washing", "Car detailing"], ["car detailing", "car wash foam", "polishing car", "detailing"]),
    "interior": (["Interiors of Mercedes-Benz automobiles", "Interiors of BMW automobiles", "Interiors of Lexus automobiles",
                  "Interiors of Toyota automobiles"], ["dashboard", "interior"]),
    "lighting": (["Automobile headlamps", "LED headlamps"], ["LED headlight", "ambient lighting", "headlight"]),
    "wrap": (["Vehicle wraps", "Matte finish automobiles"], ["car wrap", "matte", "wrapped"]),
    "audio": (["Car audio"], ["car audio", "subwoofer", "car speaker"]),
    "bodykit": (["Spoilers (automobile)", "Body kits"], ["body kit", "spoiler"]),
    "tint": (["Window tinting"], ["window tint", "tinted"]),
    "wheels": (["Alloy wheels"], ["alloy wheel", "rims"]),
}
CANDIDATES = 16
CURATED = os.path.join(HERE, "demo_videos.json")


def portrait(data):
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    # с запасом по краям — ролик медленно наезжает и сдвигается (Ken Burns)
    return ImageOps.fit(img, (810, 1440), Image.LANCZOS, centering=(0.5, 0.5))


def _titles(categories, words):
    titles = []
    for category in categories:
        titles += commons._files_in(category)
    titles += commons._files_titled(words)
    return [t for t in dict.fromkeys(titles) if t.lower().endswith((".jpg", ".jpeg"))]


def search():
    """--search: по 16 кандидатов на тему → demo_videos/candidates/<тема>/<n>.jpg и candidates.json (для ручного отбора)."""
    path = os.path.join(OUT, "candidates.json")
    found = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    for key, (categories, words) in TOPICS.items():
        titles = _titles(categories, words)
        picked = []
        for chunk in range(0, len(titles), 40):
            details = commons.info(titles[chunk:chunk + 40])
            for title in titles[chunk:chunk + 40]:
                ii = details.get(title)
                if not ii or min(ii["width"], ii["height"]) < 700 or not commons.FREE.match(commons.meta(ii)["license"] or ""):
                    continue
                picked.append(title)
                folder = os.path.join(OUT, "candidates", key)
                os.makedirs(folder, exist_ok=True)
                portrait(commons.fetch(ii["thumburl"])).resize((135, 240)).save(os.path.join(folder, f"{len(picked)}.jpg"), quality=75)
                if len(picked) == CANDIDATES:
                    break
            if len(picked) == CANDIDATES:
                break
        found[key] = picked
        print(f"{key}: {len(picked)}", flush=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(found, fh, ensure_ascii=False, indent=1)


def main():
    """Отобранные фото из demo_videos.json → demo_videos/<тема>/<n>.jpg (810×1440) и ATTRIBUTION.md."""
    curated = json.load(open(CURATED, encoding="utf-8"))
    lines = ["# Фото для демо-видео", "", "Wikimedia Commons, свободные лицензии. Только для локальной разработки.", ""]
    for key, titles in curated.items():
        folder = os.path.join(OUT, key)
        os.makedirs(folder, exist_ok=True)
        details = commons.info(titles)
        for n, title in enumerate(titles, start=1):
            ii = details.get(title)
            if not ii:
                print(f"  нет файла: {title}")
                continue
            m = commons.meta(ii)
            portrait(commons.fetch(ii["thumburl"])).save(os.path.join(folder, f"{n}.jpg"), quality=86)
            lines.append(f"- **{key} {n}** — [{title}]({m['source']}), {m['author'] or 'автор не указан'}, {m['license']}")
        print(f"{key}: {len(titles)}", flush=True)
    with open(os.path.join(OUT, "ATTRIBUTION.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    search() if "--search" in sys.argv else main()
