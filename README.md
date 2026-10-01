# Kudaibergen — бэкенд рынка автозапчастей «Кудайберген»

Покупатель отправляет запрос «Найти запчасть» продавцам рынка, те отвечают «Есть / Нет», дальше чат,
маршрут до бокса и оценка. Плюс каталог запчастей продавцов, карта рынка, статистика бокса.

## Стек

Java 21 · Spring Boot 3 · PostgreSQL 16 · Redis · Flyway · Spring Security + JWT · Centrifugo v6 (живые события) ·
MinIO/S3 (фото) · FCM (пуши) · springdoc-openapi · JUnit 5 + Testcontainers.

## Документы

| файл | что |
| --- | --- |
| [docs/BACKEND_SPEC.md](docs/BACKEND_SPEC.md) | контракт API для фронта: пути, поля, енамы, ошибки, Centrifugo, пуши |
| [docs/FRONTEND_PROMPT.md](docs/FRONTEND_PROMPT.md) | промпт для подключения мобильного приложения, контракт внутри |
| [docs/ADMIN_API.md](docs/ADMIN_API.md) | API веб-админки: вход, роли и права, журнал, эндпоинты по фазам |
| [docs/BACKEND_DESIGN.md](docs/BACKEND_DESIGN.md) | устройство бэкенда: схема данных, модули, решения |
| [docs/SPEC_GAPS.md](docs/SPEC_GAPS.md) | сверка спецификации по дизайну с кодом и принятые решения |

## Запуск

```bash
docker compose up -d          # Postgres 16, Redis, Centrifugo (8000); MinIO — docker compose --profile s3 up -d
mvn spring-boot:run           # приложение на localhost:8080
```

Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI: `/v3/api-docs` · Health: `/actuator/health`.

В dev-режиме SMS не отправляются: код пишется в лог и возвращается в ответе `POST /api/v1/auth/otp/send`
полем `debugCode` (`SMS_EXPOSE_CODE=true`).

```bash
curl -s -X POST localhost:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' -d '{"phone":"+996700123456"}'
# → {"expiresIn":120,"resendIn":42,"debugCode":"1234"}

curl -s -X POST localhost:8080/api/v1/auth/otp/verify \
  -H 'Content-Type: application/json' -d '{"phone":"+996700123456","code":"1234"}'
# → {accessToken, refreshToken, expiresIn, isNewUser, user}

curl -s -X PUT localhost:8080/api/v1/me/role -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"role":"SELLER"}'
```

### Локальная база

База должна быть в UTF-8, иначе поиск по-русски молча не работает (в локали C Postgres не переводит
кириллицу в нижний регистр; при старте в логе будет предупреждение). Для своего Postgres:

```sql
CREATE DATABASE kudaibergen TEMPLATE template0 ENCODING 'UTF8'
  LOCALE_PROVIDER icu ICU_LOCALE 'ru-RU' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';
```

### Демо-данные

```bash
python3 scripts/fetch_demo_photos.py   # один раз: настоящие фото запчастей с Wikimedia Commons (~87 шт.)
python3 scripts/demo_seed.py --yes     # нужны psycopg2 и Pillow
```

Фото отобраны вручную (`scripts/demo_photos.json`), лежат в `scripts/demo_photos/` (не в git), авторы и лицензии —
`scripts/demo_photos/ATTRIBUTION.md`. Для видов без подходящего фото и без скачанных фото рисуются иллюстрации.

Удаляет пользователей, магазины, запчасти, запросы, чаты и фото (справочники и схема рынка остаются) и заливает
живой рынок: 24 магазина по рядам (плюс тестовые клиент `+996555000001` и продавец `+996555000002`), ~300 запчастей с картинками, историю запросов за месяц с ответами, чатами и
отзывами, активные запросы для ленты продавцов. Покупатель из макета — Бакыт `+996555123456` (Camry 50 · 2012),
продавцы — `+996700100001`…`+996700100024` (список печатается в конце). Админка: суперадмин `+996555000010`,
админ рынка `+996555000011`, пароль `Kudaibergen2026`. Для админки в данных есть магазины и мастера в разных
статусах, спор за контейнер, предупреждение, блокировки и жалоба на мастера; магазины «на проверке» видны только
при `SHOP_VERIFICATION_REQUIRED=true` (иначе при старте подтверждаются сами). Только для локальной базы.

Проверить запросы ночью: `SHOP_IGNORE_WORKING_HOURS=true` — боксы считаются открытыми в любое время.

### Админка: первый суперадмин

```bash
ADMIN_BOOTSTRAP_PHONE=+996555000099 ADMIN_BOOTSTRAP_NAME="Имя Фамилия" mvn spring-boot:run
```

При старте, если активного суперадмина нет, этот номер становится `SUPER_ADMIN`. Пароль он задаёт сам по SMS-коду:
`POST /api/v1/admin/auth/password/code {phone}`, затем `POST /api/v1/admin/auth/password {phone, code, password}`.
Вход в админку: `POST /api/v1/admin/auth/login {phone, password}` → SMS-код → `POST /api/v1/admin/auth/verify`.
Остальных сотрудников суперадмин добавляет в разделе «Сотрудники» (`POST /api/v1/admin/staff`), пароль каждый
задаёт себе сам.

| Роль | Кто | Права |
| --- | --- | --- |
| `SUPER_ADMIN` | владелец, техадмин | все, всегда |
| `MARKET_ADMIN` | «Администратор рынка» | все, кроме `STAFF_MANAGE` (сотрудники и права ролей); набор можно поменять в `PUT /admin/staff/roles/MARKET_ADMIN/permissions` |

Права: `DASHBOARD_VIEW`, `EXPORT_EXCEL`, `SELLERS_VIEW`, `SELLERS_VERIFY`, `SELLERS_BLOCK`, `DISPUTES_RESOLVE`,
`MASTERS_VIEW`, `MASTERS_VERIFY`, `MASTERS_BLOCK`, `MASTERS_CREATE`, `MARKET_VIEW`, `MARKET_EDIT`,
`MARKET_MAP_PUBLISH`, `TENANTS_IMPORT`, `REQUESTS_VIEW`, `REQUESTS_MANAGE`, `COMPLAINTS_VIEW`,
`COMPLAINTS_RESOLVE`, `CONTENT_REMOVE`, `SELLER_WARN`, `DICTIONARIES_VIEW`, `DICTIONARIES_EDIT`,
`BROADCASTS_VIEW`, `BROADCASTS_SEND`, `USERS_VIEW`, `USERS_BLOCK`, `PII_VIEW` (без него телефоны маской),
`MESSAGE_USERS`, `AUDIT_VIEW`, `STAFF_MANAGE`. Что открывает каждое — в [docs/ADMIN_API.md](docs/ADMIN_API.md).

### Фоновые задачи

| Задача | Когда | Что делает |
| --- | --- | --- |
| Рассылки (`BroadcastSender`) | каждые 20 с | запланированные — фиксирует получателей и шлёт пуши батчами по 500; в 22:00–07:00 (Бишкек) стоит |
| Сводка админки (`DashboardService`) | по запросу, кэш 90 с | KPI, графики, марки без ответа; `POST /admin/dashboard/refresh` — пересчитать сразу |
| Истечение запросов и заявок | каждую минуту | статус «время вышло», пуши покупателю |
| Очистка медиа | 03:45 | неприкреплённые фото и видео старше суток |
| Refresh-токены | 03:30 | удаляет просроченные |
| Удаление аккаунтов | ночью | через 30 дней после запроса на удаление |

### Переменные окружения

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | подключение к Postgres | localhost:5432/kudaibergen |
| `JWT_SECRET` | ключ подписи HS256 (≥32 байта) | dev-значение, **в проде обязателен** |
| `SMS_PROVIDER` | `log` или `nikita` | `log` |
| `SMS_FIXED_CODE` | фиксированный код для всех входов (тест, напр. `1111`) | пусто (в проде пусто) |
| `SMS_EXPOSE_CODE` | отдавать код в ответе API | `true` (в проде `false`) |
| `SMS_URL`, `SMS_LOGIN`, `SMS_PASSWORD`, `SMS_SENDER` | шлюз nikita.kg | — |
| `CENTRIFUGO_API_URL`, `CENTRIFUGO_API_KEY` | Server API Centrifugo (чат) | localhost:8000/api, dev-ключ |
| `CENTRIFUGO_TOKEN_SECRET` | HMAC токенов клиента Centrifugo, тот же в самом Centrifugo | dev-значение, **в проде обязателен** |
| `SHOP_IGNORE_WORKING_HOURS` | только для разработки: боксы открыты в любое время, запросы доходят и ночью | `false` |
| `SHOP_VERIFICATION_REQUIRED` | проверять место продавца (QR, SMS арендатора, админ) до того, как магазин начнёт работать | `false` — магазин действует сразу |
| `ADMIN_ORIGINS` | домены веб-админки для CORS, через запятую | `http://localhost:*,http://127.0.0.1:*` |
| `ADMIN_BOOTSTRAP_PHONE`, `ADMIN_BOOTSTRAP_NAME` | первый суперадмин (создаётся при старте, если его нет) | пусто |
| `ADMIN_COOKIE_SECURE`, `ADMIN_COOKIE_SAMESITE` | атрибуты cookie `admin_refresh`; админка на другом домене — `None` (и Secure) | `true`, `Strict` |
| `OCR_PROVIDER`, `OCR_GOOGLE_API_KEY` | распознавание номера детали: `none` или `google` (Cloud Vision) | `none` |
| `MEDIA_STORAGE` | вложения чата: `local` или `s3` | `local` |
| `MEDIA_PUBLIC_URL` | для `local`: адрес сервера в ссылках на файлы. Пусто — подставляется сам: адрес, по которому обратился телефон, а вне запроса (события, пуши) — адрес компьютера в локальной сети. В проде — точный адрес | пусто |
| `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` | MinIO/S3 при `MEDIA_STORAGE=s3` | localhost:9000, бакет kudaibergen |
| `FCM_ENABLED`, `FCM_CREDENTIALS` (путь к файлу) или `FCM_CREDENTIALS_JSON` (содержимое JSON, приоритетнее) | пуши через Firebase | `false` |

Доменные настройки (лимиты запросов, окно правки ответа, медиа) — блок `app.*` в
`src/main/resources/application.yml`.

## Тесты

```bash
mvn test        # юнит-тесты и интеграционные (*IT) — для IT нужен Docker: Testcontainers поднимает Postgres и Redis
```

## Структура

```
kg.kudaibergen
├── auth          — SMS-вход, JWT, refresh-токены, SecurityConfig
├── user          — профиль, настройки, устройства для пушей
├── garage        — марки, модели, «Мой гараж»
├── category      — категории запчастей
├── market        — схема рынка, ряды и контейнеры, поиск места, маршрут, QR, GPS-калибровка
├── shop          — магазины, проверка, фото места, сотрудники, подсветка на карте
├── catalog       — запчасти продавцов, поиск, избранное, импорт из Excel
├── request       — запросы «Найти запчасть», ответы, статистика запроса, отзывы
├── chat          — чаты, Centrifugo, быстрые ответы
├── stats         — статистика бокса
├── admin         — веб-админка: сотрудники и права, вход, журнал (@Audited), поиск
├── media         — загрузка и хранение фото
├── ocr           — распознавание номера детали
├── complaint     — жалобы
├── notification  — пуши FCM, тихие часы
└── common        — ошибки (RFC 7807), идемпотентность, лимиты, пагинация
```

Внутри пакета: `Controller` → `Service` → `Repository` + `entity/`, `dto/`. Где связь между модулями получалась бы
взаимной, модуль объявляет интерфейс, а сосед его реализует: `market.ContainerTenants` и `user.UserShops` реализует `shop`.
