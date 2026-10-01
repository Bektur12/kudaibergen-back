#!/usr/bin/env python3
"""
Демо-данные для локальной разработки: живой рынок вместо тестового мусора.

ЧТО ДЕЛАЕТ
  1. Удаляет все пользовательские данные: пользователей, магазины, запчасти, запросы, чаты, отзывы, фото
     (TRUNCATE users, media … CASCADE) и файлы в uploads/. Справочники (марки, модели, категории,
     подсказки, синонимы), схему рынка, ряды и контейнеры не трогает.
  2. Заливает: 24 магазина по рядам, ~220 запчастей с фото, OEM и машинами, покупателей с гаражом,
     историю запросов за 30 дней с ответами, чатами и отзывами (из них — рейтинги и статистика бокса),
     несколько активных запросов для ленты продавцов и сценарий Бакыта (+996555123456) как в макете.

ЗАПУСК (только локальная база!)
  python3 scripts/demo_seed.py --yes
  Параметры подключения — те же переменные, что у приложения: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
  (по умолчанию localhost:5432/postgres, postgres/1111). Папка фото — MEDIA_UPLOAD_DIR (по умолчанию uploads).

После запуска: войти заново (старые токены ведут на удалённых пользователей). Код входа в dev приходит
в ответе /auth/otp/send (debugCode). Телефоны продавцов печатаются в конце.
"""
import argparse
import colorsys
import io
import json
import math
import os
import random
import secrets
import shutil
import string
import sys
import uuid
from datetime import datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

import psycopg2
from psycopg2.extras import execute_values
from PIL import Image, ImageDraw, ImageFilter, ImageFont

BISHKEK = ZoneInfo("Asia/Bishkek")
NOW = datetime.now(timezone.utc)
RNG = random.Random(20260930)
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
BASE62 = string.digits + string.ascii_uppercase + string.ascii_lowercase

# ─────────────────────────── магазины ───────────────────────────
# (название, ряд, бокс, марки, категории, часы, имя владельца, склонность к б/у)
SHOPS = [
    ("Автодеталь Азамат", "14", 12, ["toyota", "lexus"], ["suspension", "brakes", "engine", "lights", "cooling", "service"], ("08:00", "17:00"), "Азамат", 0.1),
    ("Japan Parts KG", "14", 5, ["toyota", "lexus", "honda", "nissan"], ["suspension", "brakes", "service", "engine"], ("08:00", "17:00"), "Руслан", 0.15),
    ("Honda Центр", "14", 20, ["honda"], ["suspension", "brakes", "engine", "lights", "body", "cooling", "service"], ("08:00", "17:00"), "Эрмек", 0.2),
    ("Korea Plus", "Ж", 7, ["hyundai", "kia"], ["suspension", "brakes", "body", "lights", "service"], ("08:00", "17:00"), "Тимур", 0.1),
    ("Мерс Сервис", "16", 9, ["mercedes-benz"], ["engine", "suspension", "electrics", "brakes"], ("09:00", "18:00"), "Бакай", 0.3),
    ("Бавария Запчасть", "16", 14, ["bmw", "mercedes-benz"], ["engine", "suspension", "cooling", "lights"], ("09:00", "17:30"), "Данияр", 0.25),
    ("Эрлан шрот", "Ю", 20, ["toyota", "lexus", "honda", "nissan"], ["body", "lights", "engine", "transmission", "electrics"], ("08:00", "17:00"), "Эрлан", 0.85),
    ("Nissan Pro", "12", 4, ["nissan"], ["suspension", "brakes", "engine", "cooling", "service"], ("08:00", "17:00"), "Мирлан", 0.2),
    ("Кузов Мастер", "12", 18, ["toyota", "hyundai", "kia", "honda"], ["body", "lights"], ("08:30", "17:00"), "Жаныбек", 0.4),
    ("Оптика Plus", "18", 10, ["toyota", "lexus", "honda", "nissan", "hyundai", "kia", "mercedes-benz"], ["lights"], ("08:00", "17:00"), "Нурлан", 0.3),
    ("Тормоза.kg", "18", 22, ["toyota", "lexus", "honda", "nissan", "hyundai", "kia"], ["brakes"], ("08:00", "17:00"), "Улан", 0.0),
    ("Ходовая 555", "10", 6, ["toyota", "lexus", "nissan", "mitsubishi"], ["suspension"], ("07:30", "16:30"), "Бекзат", 0.05),
    ("Масла и фильтры Нурбек", "10", 15, ["toyota", "lexus", "honda", "nissan", "hyundai", "kia", "subaru", "mitsubishi"], ["service"], ("08:00", "17:00"), "Нурбек", 0.0),
    ("Субару Центр", "С", 12, ["subaru"], ["suspension", "engine", "brakes", "service", "cooling"], ("08:00", "17:00"), "Адилет", 0.25),
    ("Митсубиси Авто", "С", 25, ["mitsubishi"], ["suspension", "engine", "brakes", "body", "service"], ("08:00", "17:00"), "Айбек", 0.3),
    ("Лада Запчасть", "1", 8, ["lada"], ["suspension", "brakes", "engine", "electrics", "body", "service"], ("08:00", "16:00"), "Сергей", 0.1),
    ("Daewoo & Chevrolet", "1", 17, ["daewoo", "chevrolet"], ["suspension", "brakes", "engine", "body", "service"], ("08:00", "17:00"), "Аскар", 0.15),
    ("VAG Parts", "8", 11, ["volkswagen", "audi"], ["engine", "suspension", "electrics", "cooling"], ("09:00", "18:00"), "Максат", 0.3),
    ("Радиаторы Бишкек", "8", 20, ["toyota", "lexus", "honda", "nissan", "hyundai", "kia", "mercedes-benz", "bmw"], ["cooling"], ("08:00", "17:00"), "Канат", 0.05),
    ("Электрик Авто", "6", 9, ["toyota", "lexus", "honda", "nissan", "hyundai", "kia"], ["electrics"], ("08:00", "17:00"), "Эмиль", 0.4),
    ("Прадо Крузак", "Я", 14, ["toyota", "lexus"], ["suspension", "engine", "transmission", "body"], ("08:00", "17:00"), "Талант", 0.3),
    ("Mazda Ford Opel", "4", 10, ["mazda", "ford", "opel"], ["suspension", "brakes", "engine", "service"], ("08:00", "17:00"), "Артур", 0.3),
    ("Корея Мотор", "Ж", 21, ["hyundai", "kia"], ["engine", "transmission", "cooling"], ("08:00", "17:00"), "Самат", 0.45),
    ("Салон и аксессуары", "Д", 8, ["toyota", "lexus", "honda"], ["interior", "other"], ("09:00", "17:00"), "Гульнара", 0.2),
]

# Модели, которые чаще встречаются на рынке (по slug марки → подстроки названия модели)
POPULAR_MODELS = {
    "toyota": ["Camry 50", "Camry 40", "Camry 70", "Corolla E150", "Prius 30", "RAV4 XA30", "Land Cruiser Prado 150",
               "Land Cruiser 200", "Highlander XU40", "Ipsum M20", "Estima R50", "Alphard H20"],
    "lexus": ["RX AL10", "RX XU30", "ES XV40", "GX 470", "LX 570"],
}

# Машины покупателя из макета: под них должно находиться много запчастей
DEMO_CARS = {"toyota": "Camry 50", "honda": "Fit GE", "lexus": "ES XV40"}

# ─────────────────────────── мастера (раздел 12) ───────────────────────────
# Рынок «Кудайберген» — около (42.847, 74.620); мастера — на соседних улицах, в 0,5–4 км
MARKET_POINT = (42.847, 74.620)
# (название, адрес, услуги, марки (None — все), страны (пусто — любые), радиус, выезд, часы, имя владельца)
MASTERS = [
    ("СТО «Ходовик»", "ул. Садыгалиева 41, бокс 3", ["CAR_REPAIR", "DIAGNOSTICS", "WHEEL_ALIGNMENT"], ["toyota", "lexus", "honda", "nissan"], ["JAPAN"], 5, False, ("09:00", "19:00"), "Эрлан"),
    ("Шиномонтаж 24/7", "ул. Ахунбаева 98", ["TIRE_SERVICE", "WHEEL_ALIGNMENT"], None, [], 8, False, ("00:00", "23:59"), "Нурбол"),
    ("Эвакуатор Бишкек", "ул. Токтоналиева 12", ["TOW_TRUCK"], None, [], 20, True, ("00:00", "23:59"), "Руслан"),
    ("Автоэлектрик Максат", "ул. Суюмбаева 150", ["AUTO_ELECTRIC", "DIAGNOSTICS"], None, [], 6, True, ("09:00", "20:00"), "Максат"),
    ("ГБО Центр", "ул. Жибек Жолу 505", ["LPG", "CAR_REPAIR"], None, ["JAPAN", "KOREA"], 10, False, ("09:00", "18:00"), "Азамат"),
    ("Мойка «Капля»", "ул. Льва Толстого 36", ["CAR_WASH", "TINTING"], None, [], 4, False, ("08:00", "22:00"), "Бекжан"),
    ("Кузовной цех «Ремикс»", "ул. Ден Сяопина 18", ["BODY_PAINT"], None, [], 10, False, ("09:00", "19:00"), "Талгат"),
    ("Корея Сервис", "ул. Байтик Баатыра 81", ["CAR_REPAIR", "DIAGNOSTICS", "OIL_CHANGE"], ["hyundai", "kia", "daewoo"], ["KOREA"], 7, False, ("09:00", "19:00"), "Тимур"),
    ("Мерс-Бавария Техцентр", "ул. Кулиева 23", ["CAR_REPAIR", "DIAGNOSTICS", "AUTO_ELECTRIC"], ["mercedes-benz", "bmw", "audi", "volkswagen"], ["EUROPE"], 10, False, ("10:00", "19:00"), "Данияр"),
    ("Экспресс-масло", "Южная магистраль 12/1", ["OIL_CHANGE"], None, [], 5, False, ("08:00", "21:00"), "Айбек"),
    ("Выездной мастер Кубат", "ул. Садыгалиева 7", ["MOBILE_MASTER", "AUTO_ELECTRIC", "TIRE_SERVICE"], None, [], 15, True, ("08:00", "22:00"), "Кубат"),
    ("Развал-схождение 3D", "ул. Ахунбаева 119", ["WHEEL_ALIGNMENT", "TIRE_SERVICE"], None, [], 6, False, ("09:00", "19:00"), "Алмаз"),
    ("Японец Сервис", "ул. Токтоналиева 44", ["CAR_REPAIR", "OIL_CHANGE", "DIAGNOSTICS"], ["toyota", "lexus", "honda", "nissan", "mazda", "mitsubishi", "subaru"], ["JAPAN", "USA"], 8, False, ("09:00", "19:00"), "Замир"),
    ("Тонировка Pro", "ул. Жибек Жолу 480", ["TINTING", "BODY_PAINT"], None, [], 8, False, ("10:00", "20:00"), "Самат"),
]
SERVICE_TEXTS = {
    "CAR_REPAIR": ["Стук в передней подвеске на кочках", "Троит двигатель на холодную", "Скрип при повороте руля"],
    "TIRE_SERVICE": ["Пробил колесо, нужна замена на запаску", "Переобуть на зимнюю резину, R17"],
    "TOW_TRUCK": ["Не заводится, нужно довезти до СТО", "Сел аккумулятор и заглохла на перекрёстке"],
    "AUTO_ELECTRIC": ["Не работают поворотники и аварийка", "Садится аккумулятор за ночь"],
    "DIAGNOSTICS": ["Горит чек, посмотреть ошибки", "Компьютерная диагностика перед покупкой"],
    "OIL_CHANGE": ["Замена масла и фильтров, 5W-30", "Масло в АКПП поменять"],
    "WHEEL_ALIGNMENT": ["Тянет вправо после ямы", "Развал-схождение после замены рычагов"],
}

# bcrypt от «Kudaibergen2026» — пароль тестовых сотрудников админки (только для локальной базы)
ADMIN_PASSWORD_HASH = "$2b$10$e22AUDpCmLw1EpijeKNhbOz221vooyIwAcPKeDjpTgvVZtxH0uhxq"

# ─────────────────────────── каталог ───────────────────────────
# (название, производители, цена от–до, сторона, позиция, б/у возможно)
CATALOG = {
    "suspension": [
        ("Стойка передняя левая", ["KYB", "Tokico", "Kayaba"], (3200, 7800), "LEFT", "FRONT", True),
        ("Стойка передняя правая", ["KYB", "Tokico", "Kayaba"], (3200, 7800), "RIGHT", "FRONT", True),
        ("Амортизаторы задние, пара", ["KYB", "Monroe"], (5200, 9800), "PAIR", "REAR", False),
        ("Рычаг передний нижний левый", ["CTR", "555", "Febest"], (2400, 5600), "LEFT", "FRONT", True),
        ("Шаровая опора", ["555", "CTR"], (900, 1900), None, "FRONT", False),
        ("Стойка стабилизатора", ["555", "CTR", "Febest"], (600, 1400), None, "FRONT", False),
        ("Сайлентблоки рычага, комплект", ["Febest", "Masuma"], (1500, 3200), None, "FRONT", False),
        ("Опора стойки передней", ["Febest", "Kayaba"], (1300, 2800), None, "FRONT", False),
        ("Ступица с подшипником", ["Koyo", "SKF", "NSK"], (3500, 7200), None, "FRONT", True),
    ],
    "brakes": [
        ("Колодки тормозные передние", ["Akebono", "Brembo", "TRW", "Sangsin"], (1800, 4600), None, "FRONT", False),
        ("Колодки тормозные задние", ["Akebono", "Brembo", "TRW", "Sangsin"], (1500, 3600), None, "REAR", False),
        ("Диски тормозные передние, пара", ["Brembo", "TRW", "Zimmermann"], (5200, 11000), "PAIR", "FRONT", False),
        ("Суппорт передний левый", ["Aisin", "оригинал"], (3500, 8200), "LEFT", "FRONT", True),
        ("Шланг тормозной передний", ["Masuma", "Febest"], (550, 1300), None, "FRONT", False),
    ],
    "engine": [
        ("Свечи зажигания Iridium, 4 шт", ["NGK", "Denso"], (2400, 4800), None, None, False),
        ("Катушка зажигания", ["Denso", "Hitachi"], (2500, 5600), None, None, True),
        ("Ремень ГРМ, комплект с роликами", ["Gates", "Aisin", "Contitech"], (4800, 9800), None, None, False),
        ("Помпа водяная", ["Aisin", "GMB"], (2800, 6200), None, None, False),
        ("Прокладка ГБЦ", ["Victor Reinz", "Elring"], (1500, 3600), None, None, False),
        ("Датчик кислорода (лямбда-зонд)", ["Denso", "Bosch", "NTK"], (3000, 7600), None, None, True),
        ("Подушка двигателя передняя", ["Febest", "Masuma"], (1800, 4300), None, "FRONT", False),
        ("Двигатель контрактный из Японии", ["оригинал"], (55000, 98000), None, None, True),
    ],
    "lights": [
        ("Фара передняя левая", ["Depo", "TYC", "Koito"], (6500, 22000), "LEFT", "FRONT", True),
        ("Фара передняя правая", ["Depo", "TYC", "Koito"], (6500, 22000), "RIGHT", "FRONT", True),
        ("Фонарь задний левый", ["Depo", "TYC"], (3500, 9200), "LEFT", "REAR", True),
        ("Противотуманная фара правая", ["Depo", "Hella"], (1800, 4200), "RIGHT", "FRONT", False),
        ("Лампы ксенон D4S, пара", ["Philips", "Osram"], (3200, 6500), "PAIR", None, False),
    ],
    "body": [
        ("Бампер передний", ["оригинал", "Китай"], (6000, 18500), None, "FRONT", True),
        ("Крыло переднее левое", ["оригинал", "Китай"], (4500, 11500), "LEFT", "FRONT", True),
        ("Капот", ["оригинал"], (9000, 26000), None, "FRONT", True),
        ("Зеркало боковое правое", ["оригинал", "Китай"], (3500, 9500), "RIGHT", None, True),
        ("Решётка радиатора", ["оригинал", "Китай"], (2500, 7200), None, "FRONT", True),
    ],
    "electrics": [
        ("Аккумулятор 60 Ач", ["Varta", "Bosch", "Rocket"], (6500, 9800), None, None, False),
        ("Генератор", ["Denso", "Bosch"], (9000, 23000), None, None, True),
        ("Стартер", ["Denso", "Bosch"], (8000, 18500), None, None, True),
        ("Блок ABS", ["оригинал"], (8000, 21000), None, None, True),
    ],
    "cooling": [
        ("Радиатор охлаждения", ["Koyorad", "Nissens", "Denso"], (7000, 16500), None, None, True),
        ("Радиатор кондиционера", ["Koyorad", "Nissens"], (6500, 13500), None, None, False),
        ("Термостат с корпусом", ["Aisin", "Gates"], (1200, 2900), None, None, False),
        ("Вентилятор радиатора в сборе", ["Denso", "оригинал"], (5500, 12500), None, None, True),
    ],
    "transmission": [
        ("Сцепление, комплект", ["Exedy", "Valeo", "Sachs"], (6500, 14500), None, None, False),
        ("ШРУС наружный", ["GKN", "555"], (3000, 6600), None, "FRONT", False),
        ("Масло АКПП ATF WS, 4 л", ["Toyota"], (4200, 5600), None, None, False),
        ("АКПП контрактная", ["оригинал"], (45000, 88000), None, None, True),
    ],
    "service": [
        ("Масляный фильтр", ["Mann", "Toyota", "Bosch", "Sakura"], (350, 950), None, None, False),
        ("Воздушный фильтр", ["Mann", "Sakura", "Bosch"], (600, 1500), None, None, False),
        ("Салонный фильтр угольный", ["Mann", "Denso"], (700, 1600), None, None, False),
        ("Масло моторное 5W-30, 4 л", ["Toyota", "Mobil 1", "ZIC", "Castrol"], (3800, 6600), None, None, False),
        ("Щётки стеклоочистителя, пара", ["Bosch", "Denso"], (1200, 2600), "PAIR", "FRONT", False),
        ("Топливный фильтр", ["Mann", "Bosch"], (900, 2300), None, None, False),
    ],
    "interior": [
        ("Коврики салона, комплект", ["Норпласт", "Seintex"], (2500, 6200), None, None, False),
        ("Чехлы сидений экокожа", ["Автопилот", "Seintex"], (7500, 16500), None, None, False),
        ("Руль с подушкой безопасности", ["оригинал"], (9000, 21000), None, None, True),
        ("Магнитола Android 10\"", ["Teyes", "Mekede"], (11000, 19500), None, None, False),
    ],
    "other": [
        ("Брызговики, комплект", ["оригинал", "Frosch"], (1200, 2600), None, None, False),
        ("Фаркоп", ["Bosal", "Лидер-плюс"], (8500, 16000), None, "REAR", False),
        ("Домкрат ромбический 2 т", ["Sparta", "Stels"], (1500, 3100), None, None, False),
    ],
}
UNIVERSAL = {"Масло моторное 5W-30, 4 л", "Аккумулятор 60 Ач", "Лампы ксенон D4S, пара", "Щётки стеклоочистителя, пара",
             "Коврики салона, комплект", "Чехлы сидений экокожа", "Магнитола Android 10\"", "Домкрат ромбический 2 т",
             "Масло АКПП ATF WS, 4 л", "Свечи зажигания Iridium, 4 шт"}

CATEGORY_COLOR = {
    "suspension": (33, 85, 230), "brakes": (200, 40, 40), "engine": (70, 70, 80), "lights": (230, 160, 20),
    "body": (40, 140, 120), "electrics": (120, 80, 220), "cooling": (20, 150, 210), "transmission": (150, 90, 40),
    "service": (30, 160, 80), "interior": (170, 60, 140), "other": (100, 110, 130),
}

REQUEST_TEXTS = {
    "suspension": ["Стойки передние, пара", "Рычаг передний нижний", "Шаровые обе", "Стойки стабилизатора", "Ступица передняя"],
    "brakes": ["Колодки передние", "Колодки задние", "Диски передние с колодками", "Суппорт передний левый"],
    "engine": ["Ремень ГРМ комплект", "Свечи и катушка", "Помпа", "Лямбда-зонд первый", "Подушка двигателя"],
    "lights": ["Фара левая", "Фара правая", "Фонарь задний левый", "Противотуманка правая"],
    "body": ["Бампер передний", "Крыло левое переднее", "Зеркало правое", "Капот"],
    "electrics": ["Генератор", "Стартер", "Аккумулятор 60"],
    "cooling": ["Радиатор", "Радиатор кондиционера", "Термостат"],
    "transmission": ["Сцепление комплект", "ШРУС наружный", "АКПП контрактная"],
    "service": ["Масляный и воздушный фильтр", "Масло 5W-30 4л", "Салонный фильтр"],
}
HAVE_MESSAGES = ["Есть KYB и оригинал", "Есть, новые, подойдут", "Есть б/у с Японии, состояние отличное", "Есть в наличии, подходите",
                 "Оригинал, гарантия 2 недели", "Есть два варианта: оригинал и Китай", "Есть, отложу если нужно", None, None]
BUYER_LINES = ["Здравствуйте, отложите пожалуйста, буду через час", "Сколько последняя цена?", "А на 2012 год точно подойдёт?",
               "Есть фото?", "Хорошо, еду", "Скиньте точное место, пожалуйста"]
SELLER_LINES = ["Хорошо, отложил", "Подойдёт, проверял на такой же", "Для вас скидка 200 сом", "Ряд 14, от центрального прохода второй бокс",
                "Ждём, до 17:00 работаем"]
REVIEW_REPLIES = ["Спасибо, приходите ещё!", "Рады помочь 🙏", "Спасибо за отзыв!"]
BUYERS = ["Айбек", "Нурлан", "Эрмек", "Азамат", "Бекзат", "Мирлан", "Тимур", "Данияр", "Улан", "Жаныбек", "Айгерим", "Чолпон",
          "Алмаз", "Кубат", "Айдана", "Замир"]


def public_id():
    return "".join(secrets.choice(BASE62) for _ in range(10))


def phone(prefix, n):
    return f"+996{prefix}{n:06d}"


def bishkek(dt_local):
    return dt_local.replace(tzinfo=BISHKEK).astimezone(timezone.utc)


def market_time(days_ago, rng):
    """Случайный момент рабочего дня рынка days_ago дней назад (08:10–16:40 по Бишкеку, без понедельника)."""
    day = (NOW.astimezone(BISHKEK) - timedelta(days=days_ago)).date()
    if day.isoweekday() == 1:
        day -= timedelta(days=1)
    minute = rng.randint(8 * 60 + 10, 16 * 60 + 40)
    return bishkek(datetime.combine(day, time(minute // 60, minute % 60, rng.randint(0, 59))))


def round_price(value):
    step = 50 if value < 3000 else 100 if value < 20000 else 1000
    return int(round(value / step) * step)


def oem_for(brand, rng):
    r = lambda n: "".join(rng.choice(string.digits) for _ in range(n))
    a = lambda n: "".join(rng.choice(string.ascii_uppercase + string.digits) for _ in range(n))
    if brand in ("toyota", "lexus"):
        return f"{r(5)}-{a(5)}"
    if brand == "honda":
        return f"{r(5)}-{a(3)}-{a(3)}"
    if brand == "nissan":
        return f"{r(5)}-{a(5)}"
    if brand in ("hyundai", "kia"):
        return f"{r(5)}-{a(5)}"
    if brand == "mercedes-benz":
        return f"A{r(10)}"
    if brand == "bmw":
        return r(11)
    return f"{a(3)}{r(6)}"


# ─────────────────────────── картинки ───────────────────────────

def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def draw_icon(d, category, cx, cy, s, color):
    dark = tuple(max(0, int(c * 0.55)) for c in color)
    mid = tuple(min(255, int(c * 0.85 + 30)) for c in color)
    light = tuple(min(255, int(c * 0.35 + 170)) for c in color)
    steel, steel_dark = (196, 202, 212), (120, 128, 140)
    if category == "suspension":
        d.rounded_rectangle([cx - s * .13, cy - s * .55, cx + s * .13, cy + s * .2], radius=s * .05, fill=steel, outline=steel_dark, width=6)
        d.rounded_rectangle([cx - s * .09, cy + s * .15, cx + s * .09, cy + s * .62], radius=s * .04, fill=dark)
        for i in range(7):
            y = cy - s * .42 + i * s * .1
            d.arc([cx - s * .3, y - s * .05, cx + s * .3, y + s * .05], 0, 360, fill=color, width=int(s * .045))
        d.ellipse([cx - s * .08, cy - s * .7, cx + s * .08, cy - s * .54], fill=steel_dark)
    elif category == "brakes":
        d.ellipse([cx - s * .55, cy - s * .55, cx + s * .55, cy + s * .55], fill=steel, outline=steel_dark, width=8)
        d.ellipse([cx - s * .2, cy - s * .2, cx + s * .2, cy + s * .2], fill=steel_dark)
        for k in range(5):
            a = k * 2 * math.pi / 5
            x, y = cx + math.cos(a) * s * .11, cy + math.sin(a) * s * .11
            d.ellipse([x - s * .025, y - s * .025, x + s * .025, y + s * .025], fill=steel)
        for k in range(10):
            a = k * 2 * math.pi / 10 + .3
            x, y = cx + math.cos(a) * s * .38, cy + math.sin(a) * s * .38
            d.ellipse([x - s * .02, y - s * .02, x + s * .02, y + s * .02], fill=steel_dark)
        d.pieslice([cx + s * .05, cy - s * .7, cx + s * .75, cy + s * .05], 300, 30, fill=color)
    elif category == "engine":
        teeth = 12
        pts = []
        for k in range(teeth * 2):
            a = k * math.pi / teeth
            rr = s * (.55 if k % 2 == 0 else .44)
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        d.polygon(pts, fill=steel, outline=steel_dark)
        d.ellipse([cx - s * .3, cy - s * .3, cx + s * .3, cy + s * .3], fill=color)
        d.ellipse([cx - s * .12, cy - s * .12, cx + s * .12, cy + s * .12], fill=light)
    elif category == "lights":
        d.rounded_rectangle([cx - s * .62, cy - s * .38, cx + s * .62, cy + s * .38], radius=s * .25, fill=(235, 240, 248), outline=steel_dark, width=8)
        d.ellipse([cx - s * .52, cy - s * .26, cx - s * .04, cy + s * .26], fill=light, outline=color, width=10)
        d.ellipse([cx + s * .02, cy - s * .2, cx + s * .42, cy + s * .2], fill=(250, 250, 255), outline=steel_dark, width=6)
        d.ellipse([cx - s * .36, cy - s * .1, cx - s * .2, cy + s * .06], fill=(255, 255, 255))
    elif category == "body":
        d.rounded_rectangle([cx - s * .7, cy - s * .2, cx + s * .7, cy + s * .25], radius=s * .2, fill=color)
        d.rounded_rectangle([cx - s * .5, cy - s * .05, cx + s * .5, cy + s * .12], radius=s * .06, fill=dark)
        d.rounded_rectangle([cx - s * .66, cy + s * .02, cx - s * .52, cy + s * .14], radius=s * .03, fill=light)
        d.rounded_rectangle([cx + s * .52, cy + s * .02, cx + s * .66, cy + s * .14], radius=s * .03, fill=light)
    elif category == "electrics":
        d.rounded_rectangle([cx - s * .55, cy - s * .38, cx + s * .55, cy + s * .45], radius=s * .06, fill=(45, 50, 60))
        d.rectangle([cx - s * .55, cy - s * .38, cx + s * .55, cy - s * .22], fill=color)
        d.rectangle([cx - s * .4, cy - s * .5, cx - s * .24, cy - s * .38], fill=steel_dark)
        d.rectangle([cx + s * .24, cy - s * .5, cx + s * .4, cy - s * .38], fill=steel_dark)
        d.polygon([(cx + s * .05, cy - s * .15), (cx - s * .15, cy + s * .12), (cx, cy + s * .12), (cx - s * .06, cy + s * .36),
                   (cx + s * .16, cy + s * .05), (cx + s * .02, cy + s * .05)], fill=(255, 210, 60))
    elif category == "cooling":
        d.rounded_rectangle([cx - s * .6, cy - s * .45, cx + s * .6, cy + s * .45], radius=s * .05, fill=steel_dark)
        d.rectangle([cx - s * .5, cy - s * .38, cx + s * .5, cy + s * .38], fill=(225, 230, 238))
        for k in range(15):
            x = cx - s * .47 + k * s * .067
            d.line([x, cy - s * .38, x, cy + s * .38], fill=steel_dark, width=4)
        d.rectangle([cx - s * .68, cy - s * .3, cx - s * .6, cy - s * .15], fill=color)
        d.rectangle([cx + s * .6, cy + s * .15, cx + s * .68, cy + s * .3], fill=color)
    elif category == "transmission":
        for ox, oy, rr, tint in ((-.2, .05, .38, steel), (.3, -.18, .24, color)):
            teeth = 14 if rr > .3 else 10
            pts = []
            for k in range(teeth * 2):
                a = k * math.pi / teeth
                q = s * rr * (1 if k % 2 == 0 else .82)
                pts.append((cx + ox * s + math.cos(a) * q, cy + oy * s + math.sin(a) * q))
            d.polygon(pts, fill=tint, outline=steel_dark)
            d.ellipse([cx + ox * s - s * rr * .3, cy + oy * s - s * rr * .3, cx + ox * s + s * rr * .3, cy + oy * s + s * rr * .3], fill=(240, 242, 246))
    elif category == "service":
        d.rounded_rectangle([cx - s * .32, cy - s * .5, cx + s * .32, cy + s * .5], radius=s * .1, fill=color)
        for k in range(6):
            y = cy - s * .35 + k * s * .14
            d.line([cx - s * .32, y, cx + s * .32, y], fill=dark, width=6)
        d.ellipse([cx - s * .32, cy - s * .6, cx + s * .32, cy - s * .4], fill=steel, outline=steel_dark, width=5)
        d.ellipse([cx - s * .1, cy - s * .55, cx + s * .1, cy - s * .45], fill=steel_dark)
    elif category == "interior":
        d.rounded_rectangle([cx - s * .3, cy - s * .6, cx + s * .22, cy + s * .1], radius=s * .15, fill=color)
        d.rounded_rectangle([cx - s * .4, cy + s * .02, cx + s * .45, cy + s * .32], radius=s * .1, fill=dark)
        d.rectangle([cx - s * .05, cy + s * .32, cx + s * .05, cy + s * .55], fill=steel_dark)
    else:
        d.rounded_rectangle([cx - s * .5, cy - s * .35, cx + s * .5, cy + s * .45], radius=s * .05, fill=(214, 180, 130))
        d.polygon([(cx - s * .5, cy - s * .35), (cx, cy - s * .6), (cx + s * .5, cy - s * .35)], fill=(190, 155, 105))
        d.rectangle([cx - s * .06, cy - s * .35, cx + s * .06, cy + s * .45], fill=color)


def part_image(category, manufacturer, variant, rng):
    size = 1080
    base = CATEGORY_COLOR[category]
    h, l, s = colorsys.rgb_to_hls(*(c / 255 for c in base))
    h = (h + (variant - 1) * 0.02) % 1
    top = tuple(int(c * 255) for c in colorsys.hls_to_rgb(h, 0.965, min(1, s * .6)))
    bottom = (250, 251, 253)
    img = Image.new("RGB", (size, size), bottom)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / size
        d.line([(0, y), (size, y)], fill=tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)))
    shadow = Image.new("L", (size, size), 0)
    ImageDraw.Draw(shadow).ellipse([size * .2, size * .78, size * .8, size * .86], fill=90)
    img.paste((150, 155, 165), (0, 0), shadow.filter(ImageFilter.GaussianBlur(28)))
    d = ImageDraw.Draw(img)
    angle_shift = (variant - 1) * 30
    draw_icon(d, category, size / 2 + angle_shift, size * .46, size * .52, base)
    label = manufacturer.upper() if manufacturer.isascii() else manufacturer
    f = font(FONT_BOLD, 46)
    w = d.textlength(label, font=f)
    d.rounded_rectangle([60, size - 150, 60 + w + 60, size - 70], radius=40, fill=(255, 255, 255), outline=(225, 228, 235), width=3)
    d.text((90, size - 136), label, font=f, fill=(30, 35, 45))
    return img


PHOTO_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo_photos")


def real_photos(title):
    """Отобранные фото с Wikimedia Commons (scripts/fetch_demo_photos.py) для вида запчасти; нет — пусто."""
    if not os.path.isdir(PHOTO_DIR):
        return []
    kinds = [k for k in os.listdir(PHOTO_DIR) if os.path.isdir(os.path.join(PHOTO_DIR, k)) and title.startswith(k)]
    if not kinds:
        return []
    folder = os.path.join(PHOTO_DIR, max(kinds, key=len))
    return sorted(os.path.join(folder, f) for f in os.listdir(folder) if f.endswith(".jpg"))


def save_media(cur, upload_dir, owner_id, purpose, img):
    key = f"{purpose.lower()}/{uuid.uuid4()}"
    sizes = {}
    for px in (1080, 320):
        path = os.path.join(upload_dir, f"{key}-{px}.jpg")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        out = img if px == 1080 else img.resize((px, px), Image.LANCZOS)
        buf = io.BytesIO()
        out.save(buf, "JPEG", quality=85, optimize=True)
        with open(path, "wb") as fh:
            fh.write(buf.getvalue())
        sizes[px] = buf.tell()
    cur.execute("""insert into media (owner_id, purpose, key_1080, key_320, width, height, size_bytes, created_at)
                   values (%s, %s, %s, %s, %s, %s, %s, %s) returning id""",
                (owner_id, purpose, f"{key}-1080.jpg", f"{key}-320.jpg", img.width, img.height, sizes[1080],
                 NOW - timedelta(days=40)))
    return cur.fetchone()[0]


# ─────────────────────────── заливка ───────────────────────────

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--yes", action="store_true", help="подтвердить удаление пользовательских данных")
    args = parser.parse_args()
    db = dict(host=os.getenv("DB_HOST", "localhost"), port=int(os.getenv("DB_PORT", "5432")),
              dbname=os.getenv("DB_NAME", "postgres"), user=os.getenv("DB_USER", "postgres"),
              password=os.getenv("DB_PASSWORD", "1111"))
    upload_dir = os.path.abspath(os.getenv("MEDIA_UPLOAD_DIR", "uploads"))
    if db["host"] not in ("localhost", "127.0.0.1"):
        sys.exit("Только для локальной базы: DB_HOST должен быть localhost")
    if not args.yes:
        sys.exit("Скрипт удалит всех пользователей, магазины, запчасти, запросы, чаты и фото. Запустите с --yes")

    conn = psycopg2.connect(**db)
    cur = conn.cursor()

    # ── очистка ──
    cur.execute("""truncate users, media, idempotency_keys, device_tokens, refresh_tokens, complaints cascade""")
    for sub in os.listdir(upload_dir) if os.path.isdir(upload_dir) else []:
        target = os.path.join(upload_dir, sub)
        shutil.rmtree(target) if os.path.isdir(target) else os.remove(target)

    # ── справочники ──
    cur.execute("select id, slug from brands")
    brand_id = {slug: i for i, slug in cur.fetchall()}
    cur.execute("select id, slug from categories")
    category_id = {slug: i for i, slug in cur.fetchall()}
    cur.execute("select m.id, b.slug, m.name, coalesce(m.generation, ''), m.year_from, m.year_to from models m join brands b on b.id = m.brand_id")
    models = {}
    for mid, bslug, name, gen, yf, yt in cur.fetchall():
        models.setdefault(bslug, []).append(dict(id=mid, label=f"{name} {gen}".strip(), year_from=yf, year_to=yt or 2025))
    cur.execute("select id, text_ru, category_id from part_hints")
    hints_by_category = {}
    for hid, text, cid in cur.fetchall():
        hints_by_category.setdefault(cid, []).append(hid)
    cur.execute("""select c.id, r.code, r.id, c.number, c.side from containers c join market_rows r on r.id = c.row_id
                   where c.is_active and r.is_active""")
    containers = cur.fetchall()

    def model_pick(bslug):
        pool = models[bslug]
        popular = POPULAR_MODELS.get(bslug)
        if popular and RNG.random() < .75:
            pool = [m for m in pool if m["label"] in popular] or pool
        return RNG.choice(pool)

    # ── пользователи ──
    def add_user(phone_number, name, role, created):
        cur.execute("""insert into users (phone, name, role, lang, is_blocked, onboarded_at, created_at, updated_at)
                       values (%s, %s, %s, 'RU', false, %s, %s, %s) returning id""",
                    (phone_number, name, role, created, created, created))
        uid = cur.fetchone()[0]
        cur.execute("insert into user_settings (user_id) values (%s)", (uid,))
        return uid

    def add_car(uid, bslug, model, year, engine=None, primary=False):
        cur.execute("""insert into cars (user_id, brand_id, model_id, year, engine, is_primary, created_at, updated_at)
                       values (%s, %s, %s, %s, %s, %s, now(), now()) returning id""",
                    (uid, brand_id[bslug], model["id"], year, engine, primary))
        return cur.fetchone()[0]

    bakyt = add_user("+996555123456", "Бакыт", "BUYER", NOW - timedelta(days=60))
    camry = next(m for m in models["toyota"] if m["label"] == "Camry 50")
    fit = next(m for m in models["honda"] if m["label"].startswith("Fit GE"))
    bakyt_camry = add_car(bakyt, "toyota", camry, 2012, "2.5 бензин", True)
    bakyt_fit = add_car(bakyt, "honda", fit, 2009, "1.3", False)

    buyers = []
    for n, name in enumerate(BUYERS, start=1):
        uid = add_user(phone("555", 200000 + n), name, "BUYER", NOW - timedelta(days=RNG.randint(20, 120)))
        bslug = RNG.choice(["toyota"] * 5 + ["lexus", "honda", "honda", "nissan", "hyundai", "kia", "mercedes-benz", "subaru", "mitsubishi", "lada", "daewoo"])
        model = model_pick(bslug)
        year = RNG.randint(model["year_from"], min(model["year_to"], 2024))
        car = add_car(uid, bslug, model, year, None, True)
        buyers.append(dict(id=uid, car=car, brand=bslug, model=model, year=year))

    # ── магазины ──
    used_containers = set()

    def container_for(code, number):
        # нужный номер или ближайший свободный в этом ряду; северная / западная сторона — первой
        free = [c for c in containers if c[1] == code and c[0] not in used_containers]
        free.sort(key=lambda c: (abs(c[3] - number), 0 if c[4] in ("NORTH", "WEST") else 1))
        pick = free[0]
        used_containers.add(pick[0])
        return pick

    shops = []
    for n, (name, row, number, brands, cats, hours, owner_name, used_share) in enumerate(SHOPS, start=1):
        created = NOW - timedelta(days=RNG.randint(45, 200))
        owner = add_user(phone("700", 100000 + n), owner_name, "SELLER", created)
        container = container_for(row, number)
        work_days = 127 if RNG.random() < .3 else 126  # по понедельникам рынок почти пустой
        cur.execute("""insert into shops (owner_id, name, container_id, status, verified_at, open_from, open_to, work_days, is_open,
                                          phone, phone_visible, rating, reviews_count, created_at, updated_at, public_id)
                       values (%s, %s, %s, 'ACTIVE', %s, %s, %s, %s, true, %s, %s, 0, 0, %s, %s, %s) returning id""",
                    (owner, name, container[0], created, hours[0], hours[1], work_days, phone("700", 100000 + n),
                     RNG.random() < .8, created, created, public_id()))
        sid = cur.fetchone()[0]
        cur.execute("insert into shop_members (shop_id, user_id, role, created_at) values (%s, %s, 'OWNER', %s)", (sid, owner, created))
        execute_values(cur, "insert into shop_brands (shop_id, brand_id) values %s", [(sid, brand_id[b]) for b in brands])
        execute_values(cur, "insert into shop_categories (shop_id, category_id) values %s", [(sid, category_id[c]) for c in cats])
        shops.append(dict(id=sid, name=name, owner=owner, brands=brands, cats=cats, used=used_share, row=container[2],
                          container=container[0], row_code=row, number=container[3], parts=[]))

    # сотрудник у пары боксов («Продавцы в боксе · 2»)
    for shop, staff_name in ((shops[0], "Нурсултан"), (shops[1], "Бахтияр")):
        staff = add_user(phone("700", 190000 + shop["id"] % 1000), staff_name, "SELLER", NOW - timedelta(days=30))
        cur.execute("insert into shop_members (shop_id, user_id, role, created_at) values (%s, %s, 'STAFF', now())", (shop["id"], staff))

    # ── запчасти ──
    all_parts = []
    for shop in shops:
        items = [(cat, item, None) for cat in shop["cats"] for item in CATALOG[cat]]
        if len(shop["cats"]) == 1:
            # узкий магазин: каждая позиция в нескольких вариантах — разные производители
            items = [(cat, item, maker) for cat, item, _ in items for maker in item[1]]
        RNG.shuffle(items)
        count = min(len(items), RNG.randint(10, 16) if len(shop["cats"]) > 1 else RNG.randint(12, 16))
        for cat, (title, makers, (lo, hi), side, position, can_be_used), fixed_maker in items[:count]:
            used = can_be_used and fixed_maker is None and RNG.random() < shop["used"]
            maker = "оригинал" if used else (fixed_maker or RNG.choice(makers))
            condition = "USED" if used else ("ON_ORDER" if RNG.random() < .08 else "NEW")
            price = round_price(RNG.uniform(lo, hi) * (.65 if used else 1))
            quantity = 0 if RNG.random() < .12 else (1 if used else RNG.randint(1, 12))
            full_title = title if maker in ("оригинал", "Китай") else f"{title} {maker}"
            if used:
                full_title += ", б/у"
            fit_brands = shop["brands"][:]
            RNG.shuffle(fit_brands)
            # Toyota — самая частая машина на рынке: у магазинов, где она есть, большинство позиций под неё
            if "toyota" in fit_brands and RNG.random() < .65:
                fit_brands.remove("toyota")
                fit_brands.insert(0, "toyota")
            main_brand = fit_brands[0]
            oem = oem_for(main_brand, RNG) if (not used and RNG.random() < .7) else None
            published = NOW - timedelta(days=RNG.randint(1, 40), hours=RNG.randint(0, 20))
            cur.execute("""insert into parts (shop_id, status, title, category_id, condition, price, quantity, manufacturer, oem_number,
                                              oem_norm, side, position, views_count, published_at, created_at, updated_at, public_id)
                           values (%s, 'ACTIVE', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0, %s, %s, %s, %s) returning id""",
                        (shop["id"], full_title, category_id[cat], condition, price, quantity,
                         None if maker in ("оригинал", "Китай") else maker, oem,
                         None if oem is None else "".join(ch for ch in oem if ch.isalnum()).upper(),
                         side, position, published, published, published, public_id()))
            pid = cur.fetchone()[0]
            # машины: универсальное — на несколько марок целиком, остальное — 1–3 модели
            fitments = []
            if title in UNIVERSAL:
                for b in fit_brands[:3]:
                    fitments.append((pid, brand_id[b], None, None, None))
            else:
                for b in fit_brands[:RNG.choice([1, 1, 2])]:
                    picked = {model_pick(b)["id"]: None for _ in range(RNG.choice([1, 2, 2, 3]))}
                    # демо-машины Бакыта (Camry 50, Fit) — у заметной доли запчастей своей марки
                    favourite = DEMO_CARS.get(b)
                    if favourite and RNG.random() < .8:
                        picked[next(x for x in models[b] if x["label"].startswith(favourite))["id"]] = None
                    for m in picked:
                        model = next(x for x in models[b] if x["id"] == m)
                        fitments.append((pid, brand_id[b], m, model["year_from"], model["year_to"] if model["year_to"] < 2025 else None))
            execute_values(cur, "insert into part_fitments (part_id, brand_id, model_id, year_from, year_to) values %s", fitments)
            # фото
            available = real_photos(title)
            if available:
                chosen = RNG.sample(available, k=min(len(available), RNG.choice([1, 2, 2, 3])))
                photos = [save_media(cur, upload_dir, shop["owner"], "PART", Image.open(path).convert("RGB")) for path in chosen]
            else:
                photos = [save_media(cur, upload_dir, shop["owner"], "PART", part_image(cat, maker, v, RNG))
                          for v in range(1, RNG.choice([1, 2, 2, 3]) + 1)]
            execute_values(cur, "insert into part_photos (part_id, sort, media_id) values %s", [(pid, i, m) for i, m in enumerate(photos)])
            # просмотры за 3 недели
            views = []
            total = 0
            for day in range(21):
                if RNG.random() < .55:
                    v = RNG.randint(1, 9)
                    total += v
                    views.append((pid, (NOW.astimezone(BISHKEK) - timedelta(days=day)).date(), v))
            if views:
                execute_values(cur, "insert into part_views_daily (part_id, day, views) values %s", views)
            cur.execute("update parts set views_count = %s where id = %s", (total + RNG.randint(0, 60), pid))
            part = dict(id=pid, cat=cat, price=price, condition=condition, shop=shop["id"], title=full_title,
                        media=photos[0], in_stock=quantity > 0)
            shop["parts"].append(part)
            all_parts.append(part)

    # ── запросы, ответы, чаты, отзывы ──
    shop_by_id = {s["id"]: s for s in shops}
    reviews_by_shop = {}

    def add_message(chat, side, sender, mtype, text, at, code=None, payload=None):
        at = min(at, NOW - timedelta(seconds=5))
        cur.execute("""insert into messages (chat_id, side, sender_id, type, text, code, payload, created_at)
                       values (%s, %s, %s, %s, %s, %s, %s, %s) returning id""",
                    (chat, side, sender, mtype, text, code, None if payload is None else json.dumps(payload), at))
        return cur.fetchone()[0]

    def create_request(buyer_id, car_id, bslug, model, year, cat, text, created, status, duration="MIN_30",
                       force_have=None, recipients_filter=None, answer_rate=.7, chat_rate=.6, close=None, fresh=False):
        minutes = {"MIN_30": 30, "HOUR_1": 60, "HOUR_3": 180}[duration]
        expires = created + timedelta(minutes=minutes)
        hint_pool = hints_by_category.get(category_id.get(cat), [])
        hint = RNG.choice(hint_pool) if hint_pool and RNG.random() < .5 else None
        cur.execute("""insert into part_requests (buyer_id, car_id, brand_id, model_id, year, text, category_id, target, status,
                                                  recipients_count, have_count, sent_at, created_at, duration, expires_at,
                                                  extended_times, target_row_ids, target_container_ids, hint_id)
                       values (%s, %s, %s, %s, %s, %s, %s, 'MARKET', %s, 0, 0, %s, %s, %s, %s, 0, '{}', '{}', %s) returning id""",
                    (buyer_id, car_id, brand_id[bslug], model["id"], year, text, category_id.get(cat), status, created, created,
                     duration, expires, hint))
        rid = cur.fetchone()[0]
        receivers = [s for s in shops if bslug in s["brands"]]
        if recipients_filter:
            receivers = [s for s in receivers if recipients_filter(s)]
        have_shops = []
        for s in receivers:
            sells = cat in s["cats"]
            seen = fresh and RNG.random() < .3 or (not fresh and RNG.random() < .85)
            answered = not fresh and seen and RNG.random() < answer_rate
            answer = None
            if force_have is not None:
                answer = "HAVE" if s["id"] in force_have else ("NOT_HAVE" if not fresh and RNG.random() < .6 else None)
                answered = answer is not None
                seen = seen or answered
            elif answered:
                answer = "HAVE" if (sells and RNG.random() < .7) or RNG.random() < .08 else "NOT_HAVE"
            latest = NOW - timedelta(seconds=40)
            replied = min(created + timedelta(minutes=RNG.randint(1, 22), seconds=RNG.randint(0, 59)), latest) if answered else None
            seen_at = (replied - timedelta(seconds=RNG.randint(10, 30))) if replied else (
                min(created + timedelta(minutes=RNG.randint(0, 15)), latest) if seen else None)
            if answer:
                rstatus = answer
            elif status == "ACTIVE":
                rstatus = "SEEN" if seen else "DELIVERED"
            else:
                rstatus = "EXPIRED"
            cur.execute("""insert into request_recipients (request_id, shop_id, notified_at, seen_at, replied_at, status, row_id, container_id)
                           values (%s, %s, %s, %s, %s, %s, %s, %s)""",
                        (rid, s["id"], created, seen_at, replied, rstatus, s["row"], s["container"]))
            if answer:
                part = next((p for p in s["parts"] if p["cat"] == cat and p["in_stock"]), None) if answer == "HAVE" else None
                condition = (part["condition"] if part else RNG.choice(["NEW", "NEW", "USED"])) if answer == "HAVE" else None
                price = (part["price"] if part else round_price(RNG.uniform(1500, 9000))) if answer == "HAVE" else None
                message = RNG.choice(HAVE_MESSAGES) if answer == "HAVE" else None
                cur.execute("""insert into request_replies (request_id, shop_id, author_id, answer, condition, message, price, created_at, updated_at, part_id)
                               values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) returning id""",
                            (rid, s["id"], s["owner"], answer, condition, message, price, replied, replied,
                             part["id"] if part and RNG.random() < .6 else None))
                reply_id = cur.fetchone()[0]
                if answer == "HAVE":
                    have_shops.append((s, reply_id, replied, price, condition, message, part))
        # чаты по «Есть»
        for s, reply_id, replied, price, condition, message, part in have_shops:
            cur.execute("""insert into chats (buyer_id, shop_id, request_id, created_at) values (%s, %s, %s, %s) returning id""",
                        (buyer_id, s["id"], rid, replied))
            chat = cur.fetchone()[0]
            add_message(chat, "SYSTEM", None, "SYSTEM", None, replied, code="PAY_AT_BOX")
            last = add_message(chat, "SHOP", s["owner"], "REPLY", message, replied,
                               payload={"replyId": reply_id, "condition": condition, "price": price, "partId": part["id"] if part else None})
            last_at = replied
            first_buyer = None
            buyer_read = last
            shop_read = last
            if RNG.random() < chat_rate:
                at = replied + timedelta(minutes=RNG.randint(1, 30))
                last = add_message(chat, "BUYER", buyer_id, "TEXT", RNG.choice(BUYER_LINES), at)
                first_buyer, last_at, buyer_read = at, at, last
                if RNG.random() < .8:
                    at += timedelta(minutes=RNG.randint(1, 15))
                    last = add_message(chat, "SHOP", s["owner"], "TEXT", RNG.choice(SELLER_LINES), at)
                    last_at, shop_read = at, last
                    buyer_read = last if not fresh else buyer_read
                if RNG.random() < .3 and not fresh:
                    at += timedelta(minutes=RNG.randint(20, 90))
                    last = add_message(chat, "BUYER", buyer_id, "QUICK", "Покупатель подошёл", at, code="ARRIVED")
                    last_at, buyer_read, shop_read = at, last, last
            cur.execute("""update chats set last_message_id = %s, last_message_at = %s, buyer_first_message_at = %s,
                                            buyer_read_message_id = %s, shop_read_message_id = %s where id = %s""",
                        (last, last_at, first_buyer, buyer_read, shop_read, chat))
        # закрытие с оценкой
        closed_with = None
        if status == "CLOSED" and have_shops:
            s, *_ = close if close else RNG.choice(have_shops)
            closed_with = s
            closed_at = max(h[2] for h in have_shops) + timedelta(hours=RNG.randint(1, 5))
            cur.execute("update part_requests set closed_with_shop_id = %s, closed_at = %s where id = %s", (s["id"], closed_at, rid))
            chat_id = None
            cur.execute("select id from chats where request_id = %s and shop_id = %s", (rid, s["id"]))
            row = cur.fetchone()
            if row:
                add_message(row[0], "SYSTEM", None, "SYSTEM", None, closed_at, code="REQUEST_CLOSED")
            if RNG.random() < .85:
                stars = RNG.choices([5, 4, 3, 2], weights=[64, 27, 7, 2])[0]
                tags = RNG.sample(["FAST_REPLY", "PART_OK", "EASY_TO_FIND"], k=RNG.randint(0, 3) if stars >= 4 else 0)
                reply = RNG.choice(REVIEW_REPLIES) if RNG.random() < .35 else None
                cur.execute("""insert into reviews (shop_id, buyer_id, request_id, stars, tags, reply_text, replied_at, created_at)
                               values (%s, %s, %s, %s, %s, %s, %s, %s)""",
                            (s["id"], buyer_id, rid, stars, tags or '{}', reply, closed_at + timedelta(hours=3) if reply else None, closed_at))
        elif status in ("EXPIRED", "CLOSED"):
            if status == "CLOSED":
                status = "EXPIRED"
            cur.execute("update part_requests set status = %s, closed_at = %s where id = %s", (status, expires, rid))
        if status == "EXPIRED" and not closed_with:
            cur.execute("update part_requests set closed_at = %s where id = %s", (expires, rid))
        cur.execute("update part_requests set recipients_count = %s, have_count = %s where id = %s", (len(receivers), len(have_shops), rid))
        return rid, have_shops

    # история за 30 дней
    for _ in range(140):
        b = RNG.choice(buyers)
        cat = RNG.choice(list(REQUEST_TEXTS))
        created = market_time(RNG.randint(1, 30), RNG)
        create_request(b["id"], b["car"], b["brand"], b["model"], b["year"], cat, RNG.choice(REQUEST_TEXTS[cat]), created,
                       RNG.choices(["CLOSED", "EXPIRED"], weights=[45, 55])[0], RNG.choice(["MIN_30", "HOUR_1", "HOUR_3"]))

    # живые запросы — лента продавцов «Новые»
    for minutes_ago, cat in ((3, "suspension"), (7, "brakes"), (11, "lights"), (18, "cooling"), (26, "service"), (34, "engine")):
        b = RNG.choice([x for x in buyers if x["brand"] in ("toyota", "lexus", "honda", "hyundai", "kia")])
        create_request(b["id"], b["car"], b["brand"], b["model"], b["year"], cat, RNG.choice(REQUEST_TEXTS[cat]),
                       NOW - timedelta(minutes=minutes_ago), "ACTIVE", "HOUR_3", fresh=True)

    # Бакыт: активный запрос с тремя «Есть» (как экраны 05 и 07), закрытый с оценкой и истёкший без ответов
    azamat, japan, erlan = shops[0], shops[1], shops[6]
    rid, _ = create_request(bakyt, bakyt_camry, "toyota", camry, 2012, "suspension", "Стойки передние, пара",
                            NOW - timedelta(minutes=12), "ACTIVE", "HOUR_3", force_have={azamat["id"], japan["id"], erlan["id"]})
    cur.execute("update part_requests set hint_id = null where id = %s", (rid,))
    # первый ответ — «Есть KYB и оригинал · 3 200 сом» от Азамата (как в макете)
    cur.execute("""update request_replies set message = 'Есть KYB и оригинал', price = 3200, condition = 'NEW',
                   created_at = %s, updated_at = %s where request_id = %s and shop_id = %s""",
                (NOW - timedelta(minutes=10), NOW - timedelta(minutes=10), rid, azamat["id"]))
    cur.execute("""update request_recipients set replied_at = %s where request_id = %s and shop_id = %s""",
                (NOW - timedelta(minutes=10), rid, azamat["id"]))
    cur.execute("select id from chats where request_id = %s and shop_id = %s", (rid, azamat["id"]))
    chat = cur.fetchone()[0]
    last = add_message(chat, "SHOP", azamat["owner"], "TEXT", "Отложил KYB для вас, работаем до 17:00", NOW - timedelta(minutes=4))
    cur.execute("update chats set last_message_id = %s, last_message_at = %s where id = %s", (last, NOW - timedelta(minutes=4), chat))

    brakes_shop = shops[10]
    create_request(bakyt, bakyt_camry, "toyota", camry, 2012, "brakes", "Колодки задние", market_time(3, RNG), "CLOSED",
                   force_have={brakes_shop["id"], azamat["id"]}, close=None)
    create_request(bakyt, bakyt_fit, "honda", fit, 2009, "cooling", "Радиатор охлаждения", market_time(6, RNG), "EXPIRED",
                   force_have=set())

    # отзывы за полгода без привязки к запросу (история до приложения): «★ 4.8 · 126 отзывов»
    for shop in shops:
        count = int(RNG.triangular(12, 140, 45))
        rows = []
        for _ in range(count):
            stars = RNG.choices([5, 4, 3, 2, 1], weights=[70, 22, 5, 2, 1])[0]
            tags = RNG.sample(["FAST_REPLY", "PART_OK", "EASY_TO_FIND"], k=RNG.randint(0, 3)) if stars >= 4 else []
            at = NOW - timedelta(days=RNG.uniform(2, 180))
            reply = RNG.choice(REVIEW_REPLIES) if RNG.random() < .3 else None
            buyer = RNG.choice(buyers)["id"] if RNG.random() < .8 else None
            rows.append((shop["id"], buyer, None, stars, tags or '{}', reply, at + timedelta(hours=5) if reply else None, at))
        execute_values(cur, """insert into reviews (shop_id, buyer_id, request_id, stars, tags, reply_text, replied_at, created_at)
                               values %s""", rows)

    # рейтинги из отзывов
    cur.execute("""update shops s set rating = coalesce(r.avg, 0), reviews_count = coalesce(r.cnt, 0)
                   from (select shop_id, round(avg(stars)::numeric, 1) avg, count(*) cnt from reviews group by shop_id) r
                   where r.shop_id = s.id""")

    # ── мастера и заявки на услуги ──
    def near(km_min, km_max):
        angle = RNG.uniform(0, 2 * 3.14159)
        dist = RNG.uniform(km_min, km_max)
        return (MARKET_POINT[0] + dist / 111.0 * __import__("math").sin(angle),
                MARKET_POINT[1] + dist / (111.0 * 0.733) * __import__("math").cos(angle))

    def add_master(owner, name, address, services, brands, origins, radius, mobile, hours, point, created):
        cur.execute("""insert into masters (owner_id, name, phone, address, lat, lng, radius_km, is_mobile, all_brands,
                                            open_from, open_to, work_days, is_accepting, status, public_id, created_at, updated_at)
                       values (%s, %s, (select phone from users where id = %s), %s, %s, %s, %s, %s, %s, %s, %s, 127, true,
                               'ACTIVE', %s, %s, %s) returning id""",
                    (owner, name, owner, address, point[0], point[1], radius, mobile, brands is None, hours[0], hours[1],
                     public_id(), created, created))
        mid = cur.fetchone()[0]
        execute_values(cur, "insert into master_services (master_id, service) values %s", [(mid, x) for x in services])
        if brands:
            execute_values(cur, "insert into master_brands (master_id, brand_id) values %s", [(mid, brand_id[b]) for b in brands])
        if origins:
            execute_values(cur, "insert into master_origins (master_id, origin) values %s", [(mid, o) for o in origins])
        return mid

    master_rows = []
    for n, (name, address, services, brands, origins, radius, mobile, hours, owner_name) in enumerate(MASTERS, start=1):
        created = NOW - timedelta(days=RNG.randint(30, 300))
        owner = add_user(phone("701", 100000 + n), owner_name, "MASTER", created)
        point = near(0.5, 4.0)
        mid = add_master(owner, name, address, services, brands, origins, radius, mobile, hours, point, created)
        master_rows.append(dict(id=mid, owner=owner, services=services, brands=brands, origins=origins, point=point,
                                radius=radius, name=name))
        rows = []
        for _ in range(int(RNG.triangular(8, 220, 60))):
            stars = RNG.choices([5, 4, 3, 2, 1], weights=[72, 20, 5, 2, 1])[0]
            tags = RNG.sample(["FAST_REPLY", "PART_OK", "EASY_TO_FIND"], k=RNG.randint(0, 2)) if stars >= 4 else []
            rows.append((mid, RNG.choice(buyers)["id"] if RNG.random() < .8 else None, stars, tags or '{}',
                         NOW - timedelta(days=RNG.uniform(1, 200))))
        execute_values(cur, "insert into master_reviews (master_id, buyer_id, stars, tags, created_at) values %s", rows)
    cur.execute("""update masters m set rating = r.avg, reviews_count = r.cnt
                   from (select master_id, round(avg(stars)::numeric, 1) avg, count(*) cnt from master_reviews group by master_id) r
                   where r.master_id = m.id""")

    # тестовый мастер: все услуги и марки, радиус 30 км, круглосуточно — любая заявка рядом с рынком доходит
    master_tester = add_user("+996555000003", "Мастер", "MASTER", NOW - timedelta(days=1))
    add_master(master_tester, "Мой сервис", "ул. Садыгалиева 1", list(SERVICE_TEXTS) + ["LPG", "CAR_WASH", "BODY_PAINT",
               "TINTING", "MOBILE_MASTER"], None, [], 30, True, ("00:00", "23:59"), MARKET_POINT, NOW - timedelta(days=1))

    # живые заявки клиентов — лента мастеров «Новые»
    brand_slug_by_id = {v: k for k, v in brand_id.items()}
    for minutes_ago, service in ((4, "TIRE_SERVICE"), (9, "CAR_REPAIR"), (15, "DIAGNOSTICS"), (21, "AUTO_ELECTRIC"), (28, "OIL_CHANGE")):
        b = RNG.choice(buyers)
        created = NOW - timedelta(minutes=minutes_ago)
        point = near(0.3, 2.0)
        cur.execute("""insert into service_requests (buyer_id, service, car_id, brand_id, model_id, year, origin, description,
                                                     when_kind, where_kind, lat, lng, radius_km, status, duration, sent_at,
                                                     expires_at, created_at)
                       values (%s, %s, %s, %s, %s, %s, %s, %s, 'TODAY', 'I_COME', %s, %s, 10, 'ACTIVE', 'HOUR_3', %s, %s, %s)
                       returning id""",
                    (b["id"], service, b["car"], brand_id[b["brand"]], b["model"]["id"], b["year"],
                     {"hyundai": "KOREA", "kia": "KOREA", "daewoo": "KOREA", "mercedes-benz": "EUROPE", "lada": "EUROPE"}.get(b["brand"], "JAPAN"),
                     RNG.choice(SERVICE_TEXTS[service]), point[0], point[1], created, created + timedelta(hours=3), created))
        sid = cur.fetchone()[0]
        count = 0
        for m in master_rows:
            works = service in m["services"] and (m["brands"] is None or b["brand"] in m["brands"])
            distance = int(((m["point"][0] - point[0]) * 111000) ** 2 + ((m["point"][1] - point[1]) * 111000 * 0.733) ** 2) ** 0.5
            if works and distance <= min(10, m["radius"]) * 1000:
                cur.execute("""insert into service_recipients (request_id, master_id, status, distance_m, notified_at)
                               values (%s, %s, 'DELIVERED', %s, %s)""", (sid, m["id"], int(distance), created))
                count += 1
        cur.execute("update service_requests set recipients_count = %s where id = %s", (count, sid))

    # тестовые аккаунты для ручной проверки: клиент с Camry 50 и продавец, открытый круглосуточно со всеми марками
    tester = add_user("+996555000001", "Клиент", "BUYER", NOW - timedelta(days=1))
    add_car(tester, "toyota", camry, 2014, "2.5 бензин", True)
    seller = add_user("+996555000002", "Продавец", "SELLER", NOW - timedelta(days=1))
    box = container_for("14", 8)
    cur.execute("""insert into shops (owner_id, name, container_id, status, verified_at, open_from, open_to, work_days, is_open,
                                      phone, phone_visible, rating, reviews_count, created_at, updated_at, public_id)
                   values (%s, 'Мой бокс', %s, 'ACTIVE', now(), '00:00', '23:59', 127, true, '+996555000002', true, 0, 0,
                           now(), now(), %s) returning id""", (seller, box[0], public_id()))
    box_id = cur.fetchone()[0]
    cur.execute("insert into shop_members (shop_id, user_id, role, created_at) values (%s, %s, 'OWNER', now())", (box_id, seller))
    cur.execute("insert into shop_brands (shop_id, brand_id) select %s, id from brands", (box_id,))
    cur.execute("insert into shop_categories (shop_id, category_id) select %s, id from categories", (box_id,))

    # сотрудники админки, пароль Kudaibergen2026 (bcrypt), второй шаг — код из debugCode
    for admin_phone, admin_role, full_name, title in (
            ("+996555000010", "SUPER_ADMIN", "Суперадмин", "Суперадмин"),
            ("+996555000011", "MARKET_ADMIN", "Айбек Т.", "Администратор рынка")):
        admin_user = add_user(admin_phone, full_name, "BUYER", NOW - timedelta(days=30))
        cur.execute("""insert into admin_members (user_id, admin_role, full_name, title, password_hash, password_changed_at)
                       values (%s, %s, %s, %s, %s, now())""", (admin_user, admin_role, full_name, title, ADMIN_PASSWORD_HASH))

    # ── админка: список арендаторов, статусы, спор, санкции ──
    # арендаторы по базе рынка: у большинства боксов телефон владельца, у части — другой («Нет в списке»)
    cur.execute("update containers set tenant_name = null, tenant_phone = null")
    cur.execute("""select s.id, s.container_id, u.phone, u.name from shops s join users u on u.id = s.owner_id
                   order by s.id""")
    for n, (sid, cid, owner_phone, owner_name) in enumerate(cur.fetchall()):
        if n % 5 == 4:
            continue                                            # «Ждёт сверки»: арендатора в базе нет
        tenant_phone = owner_phone if n % 7 != 3 else phone("777", 300000 + n)
        cur.execute("update containers set tenant_name = %s, tenant_phone = %s where id = %s",
                    (f"{owner_name or 'Арендатор'} {RNG.choice('АБДЕКМНСТ')}.", tenant_phone, cid))

    def shop_of(seller_phone):
        cur.execute("select s.id from shops s join users u on u.id = s.owner_id where u.phone = %s", (seller_phone,))
        return cur.fetchone()[0]

    admin_id = admin_user
    # на проверке (видно при SHOP_VERIFICATION_REQUIRED=true; иначе при старте подтверждаются сами)
    for seller_phone in ("+996700100020", "+996700100021"):
        sid = shop_of(seller_phone)
        cur.execute("update shops set status = 'PENDING_VERIFICATION', verified_at = null where id = %s", (sid,))
        cur.execute("""insert into shop_verifications (shop_id, container_id, method, status, created_at)
                       select id, container_id, 'ADMIN', 'PENDING', now() - interval '3 hours' from shops where id = %s""",
                    (sid,))
    sid = shop_of("+996700100022")
    cur.execute("update shops set status = 'REJECTED', block_reason = 'Нет в списке арендаторов' where id = %s", (sid,))
    sid = shop_of("+996700100023")
    cur.execute("update shops set status = 'BLOCKED', block_reason = 'Продаёт контрафакт под видом оригинала' where id = %s",
                (sid,))
    cur.execute("""insert into sanctions (target_type, target_id, type, reason, admin_id, created_at)
                   values ('SHOP', %s, 'BLOCK', 'Продаёт контрафакт под видом оригинала', %s, now() - interval '2 days')""",
                (sid, admin_id))
    sid = shop_of("+996700100024")
    cur.execute("""insert into sanctions (target_type, target_id, type, reason, admin_id, active_until, created_at)
                   values ('SHOP', %s, 'WARNING', 'Цены без наличия', %s, now() + interval '80 days',
                           now() - interval '10 days')""", (sid, admin_id))
    # спор: покупатель говорит, что контейнер продавца 19 — его
    sid = shop_of("+996700100019")
    claimant = add_user("+996555000020", "Нурлан", "BUYER", NOW - timedelta(days=2))
    cur.execute("""insert into container_disputes (container_id, claimant_user_id, current_shop_id, text, created_at)
                   select container_id, %s, id, 'Арендую этот контейнер с 2019 года, договор есть',
                          now() - interval '5 hours' from shops where id = %s""", (claimant, sid))
    # мастера: двое на проверке, один заблокирован, на одного жалоба
    cur.execute("""update masters set status = 'PENDING_VERIFICATION'
                   where owner_id in (select id from users where phone in ('+996701100010', '+996701100011'))""")
    cur.execute("""update masters set status = 'BLOCKED', block_reason = 'Не приезжал по заявкам'
                   where owner_id = (select id from users where phone = '+996701100012') returning id""")
    blocked_master = cur.fetchone()[0]
    cur.execute("""insert into sanctions (target_type, target_id, type, reason, admin_id, created_at)
                   values ('MASTER', %s, 'BLOCK', 'Не приезжал по заявкам', %s, now() - interval '1 day')""",
                (blocked_master, admin_id))
    cur.execute("""insert into complaints (author_id, type, target_id, text, created_at)
                   select %s, 'MASTER', m.id, 'Взял предоплату и не приехал', now() - interval '6 hours'
                   from masters m join users u on u.id = m.owner_id where u.phone = '+996701100013'""", (bakyt,))

    # избранное Бакыта
    for p in RNG.sample([p for p in all_parts if p["shop"] in (azamat["id"], japan["id"])], 3):
        cur.execute("insert into favorite_parts (user_id, part_id, created_at) values (%s, %s, now())", (bakyt, p["id"]))
    for s in (azamat, brakes_shop):
        cur.execute("insert into favorite_shops (user_id, shop_id, created_at) values (%s, %s, now())", (bakyt, s["id"]))

    conn.commit()

    # ── итог ──
    cur.execute("""select (select count(*) from users), (select count(*) from shops), (select count(*) from parts),
                          (select count(*) from media), (select count(*) from part_requests), (select count(*) from reviews),
                          (select count(*) from chats), (select count(*) from part_requests where status = 'ACTIVE')""")
    u, s, p, m, r, rv, ch, act = cur.fetchone()
    print(f"Готово: пользователей {u}, магазинов {s}, запчастей {p}, фото {m}, запросов {r} (активных {act}), отзывов {rv}, чатов {ch}")
    print("\nПокупатель: +996555123456 (Бакыт, Camry 50 · 2012)")
    print("Тестовые: клиент +996555000001 (Camry 50 · 2014), продавец +996555000002 («Мой бокс», открыт всегда, все марки),")
    print("          мастер +996555000003 («Мой сервис», все услуги и марки, радиус 30 км, круглосуточно)")
    print("Мастера: +996701100001…+996701100014")
    print("Админка: суперадмин +996555000010, админ рынка +996555000011, пароль Kudaibergen2026")
    print("Продавцы (вход по коду из debugCode):")
    cur.execute("""select u.phone, s.name, r.code, c.number, s.rating, s.reviews_count,
                          (select count(*) from parts p where p.shop_id = s.id)
                   from shops s join users u on u.id = s.owner_id join containers c on c.id = s.container_id
                   join market_rows r on r.id = c.row_id order by s.id""")
    for ph, name, row, num, rating, cnt, parts in cur.fetchall():
        print(f"  {ph}  {name:<24} ряд {row:<3} бокс {num:<3} ★ {rating} ({cnt})  запчастей {parts}")
    conn.close()


if __name__ == "__main__":
    main()
