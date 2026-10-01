#!/usr/bin/env python3
"""
Настоящие фото запчастей для демо-данных — с Wikimedia Commons (свободные лицензии: CC0, CC BY, CC BY-SA, PD).

  python3 scripts/fetch_demo_photos.py            # скачать отобранные фото из scripts/demo_photos.json
  python3 scripts/fetch_demo_photos.py --search   # подобрать кандидатов заново (для ручного отбора)

Фото складываются в scripts/demo_photos/ (в git не попадают), авторы и лицензии — в
scripts/demo_photos/ATTRIBUTION.md. demo_seed.py берёт фото отсюда, а если их нет — рисует иллюстрации.
"""
import argparse
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "demo_photos")
CURATED = os.path.join(HERE, "demo_photos.json")
API = "https://commons.wikimedia.org/w/api.php"
UA = "KudaibergenDemoSeed/1.0 (local development demo data)"
FREE = re.compile(r"^(CC0|CC BY(-SA)? [0-9.]+|Public domain|PD.*)$", re.I)

# вид запчасти (как в начале названия в demo_seed.CATALOG) → где искать на Commons:
# категории файлов и слова, которые должны быть в имени файла (поиск по тексту описаний слишком шумный)
SOURCES = {
    "Стойка передняя": (["Shock absorbers", "MacPherson struts", "Coilovers"], ["strut", "shock absorber", "coilover"]),
    "Амортизаторы задние": (["Shock absorbers"], ["shock absorber", "Stoßdämpfer", "amortiguador"]),
    "Рычаг передний": (["Control arms"], ["control arm", "wishbone", "Querlenker"]),
    "Шаровая опора": (["Ball joints"], ["ball joint", "Traggelenk"]),
    "Ступица": (["Wheel hubs", "Wheel bearings"], ["wheel hub", "wheel bearing", "Radlager"]),
    "Колодки тормозные": (["Brake pads"], ["brake pad", "Bremsbelag", "Bremsbeläge"]),
    "Диски тормозные": (["Brake discs"], ["brake disc", "brake rotor", "Bremsscheibe"]),
    "Суппорт": (["Brake calipers"], ["brake caliper", "Bremssattel"]),
    "Свечи зажигания": (["Spark plugs"], ["spark plug", "Zündkerze"]),
    "Катушка зажигания": (["Ignition coils"], ["ignition coil", "Zündspule"]),
    "Ремень ГРМ": (["Timing belts"], ["timing belt", "Zahnriemen"]),
    "Помпа водяная": (["Water pumps (automotive)"], ["water pump", "Wasserpumpe"]),
    "Прокладка ГБЦ": (["Head gaskets"], ["head gasket", "Zylinderkopfdichtung"]),
    "Датчик кислорода": (["Oxygen sensors"], ["oxygen sensor", "lambda sensor", "Lambdasonde"]),
    "Двигатель контрактный": (["Toyota engines"], ["engine"]),
    "Фара передняя": (["Automobile headlamps"], ["headlight", "headlamp", "Scheinwerfer"]),
    "Фонарь задний": (["Automobile rear lights"], ["tail light", "taillight", "rear light", "Rückleuchte"]),
    "Противотуманная фара": (["Fog lamps"], ["fog lamp", "fog light", "Nebelscheinwerfer"]),
    "Лампы ксенон": (["Xenon headlamps", "HID lamps"], ["xenon", "HID bulb", "D2S", "D4S"]),
    "Бампер передний": (["Automobile bumpers"], ["bumper", "Stoßstange"]),
    "Крыло переднее": (["Fenders (automobile)"], ["fender", "Kotflügel"]),
    "Зеркало боковое": (["Wing mirrors"], ["wing mirror", "side mirror", "Außenspiegel"]),
    "Решётка радиатора": (["Radiator grilles"], ["grille", "Kühlergrill"]),
    "Аккумулятор": (["Automotive batteries"], ["car battery", "Autobatterie", "Starterbatterie"]),
    "Генератор": (["Alternators (automotive)", "Alternators"], ["alternator", "Lichtmaschine"]),
    "Стартер": (["Starter motors"], ["starter motor", "Anlasser"]),
    "Радиатор охлаждения": (["Radiators (engine cooling)"], ["car radiator", "Kühler"]),
    "Термостат": (["Thermostats (automotive)"], ["thermostat"]),
    "Вентилятор радиатора": (["Radiator fans"], ["radiator fan", "cooling fan"]),
    "Сцепление": (["Clutches (automotive)", "Clutch discs"], ["clutch disc", "clutch plate", "Kupplungsscheibe"]),
    "ШРУС": (["Constant-velocity joints"], ["CV joint", "Gleichlaufgelenk"]),
    "АКПП контрактная": (["Automatic transmissions"], ["automatic transmission"]),
    "Масляный фильтр": (["Oil filters"], ["oil filter", "Ölfilter"]),
    "Воздушный фильтр": (["Air filters (automobile)"], ["air filter", "Luftfilter"]),
    "Масло моторное": (["Motor oil"], ["motor oil", "Motoröl"]),
    "Щётки стеклоочистителя": (["Windscreen wipers"], ["wiper blade", "windscreen wiper", "Scheibenwischer"]),
    "Топливный фильтр": (["Fuel filters"], ["fuel filter", "Kraftstofffilter"]),
    "Коврики салона": (["Car floor mats"], ["floor mat"]),
    "Чехлы сидений": (["Automobile seats"], ["car seat", "seat cover"]),
    "Руль с подушкой": (["Steering wheels"], ["steering wheel"]),
    "Магнитола": (["Car audio"], ["head unit", "car stereo", "car radio"]),
    "Брызговики": (["Mudflaps"], ["mud flap", "mudflap"]),
    "Фаркоп": (["Tow hitches"], ["tow hitch", "trailer hitch", "Anhängerkupplung"]),
    "Домкрат": (["Scissor jacks", "Car jacks"], ["scissor jack", "car jack", "Wagenheber"]),
}


# Wikimedia просит миниатюры только стандартных размеров и без частых запросов (https://w.wiki/GHai)
THUMB_WIDTH = 960
PAUSE = 1.5


def _get(url):
    for attempt in range(6):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            time.sleep(PAUSE)
            return data
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == 5:
                raise
            wait = int(error.headers.get("Retry-After") or 0) or 20 * (attempt + 1)
            print(f"  429, жду {wait} с")
            time.sleep(wait)
        except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
            if attempt == 5:
                raise
            print(f"  обрыв связи ({error}), повтор")
            time.sleep(10 * (attempt + 1))


def api(**params):
    params.update(format="json")
    return json.loads(_get(API + "?" + urllib.parse.urlencode(params)))


def fetch(url):
    return _get(url)


def strip_html(text):
    return re.sub(r"<[^>]+>", "", text or "").strip()


def square(data):
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(data))).convert("RGB")
    return ImageOps.fit(img, (960, 960), Image.LANCZOS, centering=(0.5, 0.5))


def info(titles):
    pages = api(action="query", titles="|".join(titles), prop="imageinfo", iiprop="url|size|mime|extmetadata",
                iiurlwidth=THUMB_WIDTH)["query"]["pages"].values()
    return {p["title"]: p["imageinfo"][0] for p in pages if "imageinfo" in p}


def meta(ii):
    m = ii.get("extmetadata", {})
    return dict(license=strip_html(m.get("LicenseShortName", {}).get("value")),
                author=strip_html(m.get("Artist", {}).get("value"))[:120],
                source=ii.get("descriptionurl"))


def _files_in(category, limit=60):
    res = api(action="query", list="categorymembers", cmtitle=f"Category:{category}", cmtype="file", cmlimit=limit)
    return [m["title"] for m in res.get("query", {}).get("categorymembers", [])]


def _files_titled(words, limit=20):
    titles = []
    for word in words:
        res = api(action="query", list="search", srnamespace=6, srlimit=limit, srsearch=f'intitle:"{word}" filetype:bitmap')
        titles += [hit["title"] for hit in res.get("query", {}).get("search", [])]
    return titles


def search():
    """Кандидаты для ручного отбора: до 12 на вид — из категорий и по имени файла, только JPEG-фотографии."""
    path = os.path.join(OUT, "candidates.json")
    found = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    for key, (categories, words) in SOURCES.items():
        if key in found:
            continue
        titles = []
        for category in categories:
            titles += _files_in(category)
        titles += _files_titled(words)
        titles = [t for t in dict.fromkeys(titles) if t.lower().endswith((".jpg", ".jpeg"))]
        picked = []
        for chunk in range(0, len(titles), 40):
            details = info(titles[chunk:chunk + 40])
            for title in titles[chunk:chunk + 40]:
                ii = details.get(title)
                if not ii or min(ii["width"], ii["height"]) < 500 or not FREE.match(meta(ii)["license"] or ""):
                    continue
                picked.append(title)
                folder = os.path.join(OUT, "candidates", key)
                os.makedirs(folder, exist_ok=True)
                square(fetch(ii["thumburl"])).resize((240, 240)).save(os.path.join(folder, f"{len(picked)}.jpg"), quality=78)
                if len(picked) == 12:
                    break
            if len(picked) == 12:
                break
        found[key] = picked
        print(f"{key}: {len(picked)}", flush=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(found, fh, ensure_ascii=False, indent=1)


def download():
    """Отобранные фото из demo_photos.json → demo_photos/<вид>/<n>.jpg (1080×1080) и ATTRIBUTION.md."""
    curated = json.load(open(CURATED, encoding="utf-8"))
    lines = ["# Фото запчастей для демо-данных", "", "Wikimedia Commons, свободные лицензии. Только для локальной разработки.", ""]
    for key, titles in curated.items():
        folder = os.path.join(OUT, key)
        os.makedirs(folder, exist_ok=True)
        details = info(titles)
        for n, title in enumerate(titles, start=1):
            ii = details.get(title)
            if not ii:
                print(f"  нет файла: {title}")
                continue
            m = meta(ii)
            square(fetch(ii["thumburl"])).save(os.path.join(folder, f"{n}.jpg"), quality=86, optimize=True)
            lines.append(f"- **{key} {n}** — [{title}]({m['source']}), {m['author'] or 'автор не указан'}, {m['license']}")
        print(f"{key}: {len(titles)}")
    with open(os.path.join(OUT, "ATTRIBUTION.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--search", action="store_true", help="подобрать кандидатов для ручного отбора")
    args = parser.parse_args()
    os.makedirs(OUT, exist_ok=True)
    try:
        search() if args.search else download()
    except OSError as error:
        sys.exit(f"Нет доступа к commons.wikimedia.org: {error}")
