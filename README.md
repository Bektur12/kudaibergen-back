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
| `OCR_PROVIDER`, `OCR_GOOGLE_API_KEY` | распознавание номера детали: `none` или `google` (Cloud Vision) | `none` |
| `MEDIA_STORAGE` | вложения чата: `local` или `s3` | `local` |
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
├── media         — загрузка и хранение фото
├── ocr           — распознавание номера детали
├── complaint     — жалобы
├── notification  — пуши FCM, тихие часы
└── common        — ошибки (RFC 7807), идемпотентность, лимиты, пагинация
```

Внутри пакета: `Controller` → `Service` → `Repository` + `entity/`, `dto/`. Где связь между модулями получалась бы
взаимной, модуль объявляет интерфейс, а сосед его реализует: `market.ContainerTenants` и `user.UserShops` реализует `shop`.
