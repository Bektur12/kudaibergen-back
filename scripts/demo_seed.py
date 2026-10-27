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

# Магазины и центры вне рынка (свой адрес и точка на карте): (название, адрес, lat, lng, марки, категории, владелец)
STORES = [
    ("Toyota центр", "пр. Мира 305, Бишкек", 42.8239, 74.5905, ["toyota", "lexus"],
     ["suspension", "brakes", "engine", "service", "lights", "cooling"], "Арсен"),
    ("Kia центр", "ул. Ибраимова 115, Бишкек", 42.8667, 74.6194, ["hyundai", "kia"],
     ["suspension", "brakes", "engine", "service", "body"], "Тимур"),
    ("Mercedes центр", "ул. Льва Толстого 36, Бишкек", 42.8655, 74.5698, ["mercedes-benz", "bmw", "volkswagen", "audi"],
     ["engine", "suspension", "electrics", "cooling"], "Эдуард"),
]

# Тюнинг и красота авто — у них товары с видео («Видео с рынка»): (название, ряд или None, бокс / адрес, lat, lng, владелец)
VIDEO_SHOPS = [
    ("Detailing Lab", None, "ул. Ахунбаева 98, Бишкек", 42.8441, 74.6123, "Ислам"),
    ("Auto Style KG", "Д", 15, None, None, "Нурис"),
    ("Тюнинг Ателье 312", None, "ул. Фрунзе 520, Бишкек", 42.8812, 74.6271, "Бекжан"),
]
# (магазин из VIDEO_SHOPS, ролик Mixkit — scripts/fetch_demo_videos.py, название, цена, «Отправлю в регионы»)
VIDEO_POSTS = [
    (0, 47833, "Полировка и керамика 9H — без разводов", 12000, False),
    (0, 47831, "Защитное покрытие кузова", 15000, False),
    (0, 47830, "Полировка кузова машинкой", 9000, False),
    (0, 35576, "Оклейка защитной плёнкой PPF", 60000, False),
    (1, 47832, "Химчистка и уход за кожей салона", 6500, False),
    (1, 49064, "Химчистка салона под ключ", 8000, False),
    (1, 13269, "Детейлинг салона", 5500, False),
    (2, 47586, "Двухфазная мойка с пеной", 1500, False),
    (2, 47588, "Бесконтактная мойка и сушка", 1200, False),
    (2, 47834, "Восстановление и полировка фар", 3500, False),
]
VIDEO_BRANDS = ["toyota", "lexus", "honda", "hyundai", "kia", "mercedes-benz", "bmw", "nissan"]

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



VIDEO_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo_videos", "stock")
# длительность роликов, с
VIDEO_SECONDS = {13269: 8, 35576: 15, 47586: 10, 47588: 24, 47830: 9, 47831: 7, 47832: 20, 47833: 7, 47834: 6, 49064: 13}
LOGO_COLORS = [(29, 70, 191), (22, 128, 61), (190, 39, 45), (32, 31, 29), (115, 52, 180), (214, 104, 22), (13, 116, 144)]


def shop_logo(name):
    """Логотип магазина: цветной круг-градиент с инициалами (как у настоящих бирок на рынке)."""
    color = LOGO_COLORS[sum(map(ord, name)) % len(LOGO_COLORS)]
    img = Image.new("RGB", (1080, 1080), color)
    d = ImageDraw.Draw(img)
    for y in range(1080):
        k = y / 1080
        d.line([(0, y), (1080, y)], fill=tuple(int(c * (1.15 - .45 * k)) if c * 1.15 < 255 else 255 for c in color))
    d.ellipse([90, 90, 990, 990], outline=(255, 255, 255), width=18)
    words = [w for w in name.replace("&", " ").replace("—", " ").split() if w[0].isalnum()]
    letters = "".join(w[0] for w in words[:2]).upper() or name[:2].upper()
    f = font(FONT_BOLD, 420 if len(letters) == 1 else 340)
    box = d.textbbox((0, 0), letters, font=f)
    d.text(((1080 - (box[2] - box[0])) / 2 - box[0], (1080 - (box[3] - box[1])) / 2 - box[1]), letters, font=f, fill=(255, 255, 255))
    return img


def shop_banner(name, photo_path):
    """Баннер профиля: фото по профилю магазина, затемнённое, с названием внизу."""
    base = Image.open(photo_path).convert("RGB").resize((1080, 1080)) if photo_path else shop_logo(name)
    base = base.filter(ImageFilter.GaussianBlur(1))
    shade = Image.new("RGB", base.size, (0, 0, 0))
    mask = Image.linear_gradient("L").resize(base.size).point(lambda v: int(v * .75))
    base = Image.composite(shade, base, mask)
    d = ImageDraw.Draw(base)
    d.text((60, 900), name, font=font(FONT_BOLD, 84), fill=(255, 255, 255))
    return base


def save_video(cur, upload_dir, owner_id, stock_id):
    """Ролик Mixkit (scripts/demo_videos/stock/<id>.mp4 и обложка <id>.jpg) → media VIDEO; нет файла — None."""
    source = os.path.join(VIDEO_DIR, f"{stock_id}.mp4")
    poster_path = os.path.join(VIDEO_DIR, f"{stock_id}.jpg")
    if not os.path.exists(source) or not os.path.exists(poster_path):
        return None
    key = f"part/video/{uuid.uuid4()}"
    video_path = os.path.join(upload_dir, key + ".mp4")
    os.makedirs(os.path.dirname(video_path), exist_ok=True)
    shutil.copyfile(source, video_path)
    poster = Image.open(poster_path).convert("RGB")
    keys = {}
    for px, size in ((1080, (1280, 720)), (320, (320, 180))):
        path = os.path.join(upload_dir, f"{key}-{px}.jpg")
        poster.resize(size, Image.LANCZOS).save(path, "JPEG", quality=85)
        keys[px] = f"{key}-{px}.jpg"
    cur.execute("""insert into media (owner_id, purpose, kind, file_key, mime_type, duration_sec, key_1080, key_320, width, height,
                                      size_bytes, created_at)
                   values (%s, 'PART', 'VIDEO', %s, 'video/mp4', %s, %s, %s, 1280, 720, %s, %s) returning id""",
                (owner_id, key + ".mp4", VIDEO_SECONDS.get(stock_id, 12), keys[1080], keys[320], os.path.getsize(video_path),
                 NOW - timedelta(days=5)))
    return cur.fetchone()[0]


if __name__ == "__main__":
    main()
