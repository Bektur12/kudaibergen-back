#!/usr/bin/env python3
"""
Аккаунты для проверки App Store / Google Play — по одному на роль, повторный запуск ничего не дублирует.

  +996100000101  покупатель «Азамат», машина Toyota Camry 50 2015
  +996100000102  продавец, магазин «Демо-магазин (проверка)» вне рынка — Toyota, Lexus
  +996100000103  мастер «Демо-мастер (проверка)» — ремонт, диагностика

Префикс 100 не выдаётся операторами — номера ничьи. Вход по постоянному коду: на сервере
OTP_REVIEW_PHONES=+996100000101,+996100000102,+996100000103 и OTP_REVIEW_CODE=<код>; SMS на них не уходят.
После одобрения демо-магазин и демо-мастера можно скрыть в админке.

  DB_HOST=… DB_PORT=5432 DB_NAME=… DB_USER=… DB_PASSWORD=… python3 scripts/review_accounts.py
"""
import os
import secrets
import string

import psycopg2

BASE62 = string.ascii_letters + string.digits
BUYER, SELLER, MASTER = "+996100000101", "+996100000102", "+996100000103"


def public_id():
    return "".join(secrets.choice(BASE62) for _ in range(10))


def user(cur, phone, name, role):
    cur.execute("select id from users where phone = %s", (phone,))
    row = cur.fetchone()
    if row:
        return row[0], False
    cur.execute("""insert into users (phone, name, role, lang, is_blocked, onboarded_at, created_at, updated_at)
                   values (%s, %s, %s, 'RU', false, now(), now(), now()) returning id""", (phone, name, role))
    uid = cur.fetchone()[0]
    cur.execute("insert into user_settings (user_id) values (%s)", (uid,))
    return uid, True


def main():
    db = dict(host=os.getenv("DB_HOST", "localhost"), port=int(os.getenv("DB_PORT", "5432")),
              dbname=os.getenv("DB_NAME", "postgres"), user=os.getenv("DB_USER", "postgres"),
              password=os.getenv("DB_PASSWORD", ""))
    conn = psycopg2.connect(**db)
    cur = conn.cursor()

    buyer, new = user(cur, BUYER, "Азамат", "BUYER")
    if new:
        cur.execute("""insert into cars (user_id, brand_id, model_id, year, is_primary, created_at, updated_at)
                       select %s, m.brand_id, m.id, 2015, true, now(), now()
                       from models m join brands b on b.id = m.brand_id
                       where b.slug = 'toyota' and m.name = 'Camry' and m.generation = '50'""", (buyer,))

    seller, new = user(cur, SELLER, "Продавец (проверка)", "SELLER")
    if new:
        cur.execute("""insert into shops (owner_id, name, container_id, address, lat, lng, status, verified_at, open_from,
                                          open_to, work_days, is_open, phone, phone_visible, rating, reviews_count,
                                          created_at, updated_at, public_id)
                       values (%s, 'Демо-магазин (проверка)', null, 'ул. Ахунбаева 98, Бишкек', 42.8466, 74.5866,
                               'ACTIVE', now(), '00:00', '23:59', 127, true, %s, false, 0, 0, now(), now(), %s)
                       returning id""", (seller, SELLER, public_id()))
        shop = cur.fetchone()[0]
        cur.execute("insert into shop_members (shop_id, user_id, role, created_at) values (%s, %s, 'OWNER', now())",
                    (shop, seller))
        cur.execute("insert into shop_brands (shop_id, brand_id) select %s, id from brands where slug in ('toyota', 'lexus')",
                    (shop,))
        cur.execute("insert into shop_categories (shop_id, category_id) select %s, id from categories", (shop,))

    master, new = user(cur, MASTER, "Мастер (проверка)", "MASTER")
    if new:
        cur.execute("""insert into masters (owner_id, name, phone, address, lat, lng, radius_km, is_mobile, all_brands,
                                            open_from, open_to, work_days, is_accepting, status, public_id, created_at,
                                            updated_at)
                       values (%s, 'Демо-мастер (проверка)', %s, 'ул. Садыгалиева 41, Бишкек', 42.8590, 74.6290, 30,
                               false, true, '00:00', '23:59', 127, true, 'ACTIVE', %s, now(), now()) returning id""",
                    (master, MASTER, public_id()))
        mid = cur.fetchone()[0]
        cur.execute("insert into master_services (master_id, service) values (%s, 'CAR_REPAIR'), (%s, 'DIAGNOSTICS')",
                    (mid, mid))

    conn.commit()
    print("Готово:", BUYER, "покупатель;", SELLER, "продавец;", MASTER, "мастер")


if __name__ == "__main__":
    main()
