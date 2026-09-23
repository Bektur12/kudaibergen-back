# Kudaibergen — бэкенд маркетплейса автозапчастей

Реализация ТЗ v1.0: веерный запрос покупателя → массовый ответ магазинов → сделка, чат, аналитика.

## Стек

Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Spring Data JPA · Flyway · Spring Security + JWT ·
springdoc-openapi · JUnit 5 + Testcontainers.

## Запуск

```bash
docker compose up -d          # PostgreSQL 16 на localhost:5432
mvn spring-boot:run           # приложение на localhost:8080
```

Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI: `/v3/api-docs`
Health: `/actuator/health`

Гайд для команды фронтенда (авторизация, флоу продукта, WebSocket-чат, форматы ошибок) —
[INTEGRATION.md](INTEGRATION.md).

В dev-режиме SMS не отправляются: код пишется в лог и возвращается в ответе
`POST /auth/request-code` полем `debugCode` (`app.sms.expose-code=true`).

### Быстрая проверка вручную

```bash
curl -s -X POST localhost:8080/api/v1/auth/request-code \
  -H 'Content-Type: application/json' -d '{"phone":"+996700123456"}'
# → {"expiresInSeconds":120,"debugCode":"1234"}

curl -s -X POST localhost:8080/api/v1/auth/verify \
  -H 'Content-Type: application/json' -d '{"phone":"+996700123456","code":"1234"}'
# → accessToken / refreshToken / isNewUser

curl -s -X POST localhost:8080/api/v1/auth/register-role -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"role":"SELLER","name":"Азамат","storeName":"АвтоПрофи"}'
```

### Переменные окружения

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | подключение к Postgres | localhost:5432/kudaibergen |
| `JWT_SECRET` | ключ подписи HS256 (≥32 байта) | dev-значение, **в проде обязателен** |
| `SMS_PROVIDER` | `log` или `nikita` | `log` |
| `SMS_EXPOSE_CODE` | отдавать код в ответе API | `true` (в проде `false`) |
| `SMS_URL`, `SMS_LOGIN`, `SMS_PASSWORD`, `SMS_SENDER` | шлюз nikita.kg | — |
| `FCM_ENABLED`, `FCM_CREDENTIALS` (путь к файлу) или `FCM_CREDENTIALS_JSON` (содержимое JSON, приоритетнее) | пуши через Firebase | `false` |

Доменные настройки (TTL запросов, лимиты, окно батчинга пушей) — блок `app.*` в
`src/main/resources/application.yml`.

## Тесты

```bash
mvn test        # нужен запущенный Docker: Testcontainers поднимает Postgres 16
```

Покрыты сквозные сценарии: вход по SMS, веерная рассылка с проверкой матчинга,
массовый ответ шаблоном, прямой чат с магазином, отзыв и рейтинг,
аналитика продавца, идемпотентность, продление и отмена запроса, права доступа.

## Структура

```
kg.kudaibergen
├── auth          — SMS-вход, JWT, SecurityConfig, шлюзы SMS
├── user          — профиль, автомобили покупателя
├── store         — магазины, филиалы, категории, шаблоны ответов
├── request       — запросы + веерная рассылка + лента продавца
├── offer         — предложения, массовый ответ, протухание
├── chat          — чаты и сообщения
├── review        — отзывы и пересчёт рейтинга
├── analytics     — статистика продавца (нативный SQL)
├── notification  — outbox, диспетчер пушей, устройства
└── common        — ошибки, идемпотентность, конфиги, пагинация, PartCategory
```

Внутри пакета: `Controller` → `Service` → `Repository` + `entity/`, `dto/`.
Контроллер в репозиторий не ходит.

Зависимости между пакетами односторонние. Там, где связь получалась бы взаимной,
пакет объявляет интерфейс, а сосед его реализует:

- `request.RequestOffersView` — предложения в карточке запроса (реализует `offer`);
- `store.DealAccess` — «была ли принятая сделка» (реализует `offer`), нужен для скрытия телефонов.

## Ключевые механизмы

**Веерная рассылка** (`RequestService.create`) — магазины подбираются одним запросом
по `store_categories` + городу филиала, получатели пишутся в `request_recipients`,
пуши уходят в `notification_outbox`, а не в FCM из HTTP-потока. Ответ содержит
`sellersMatched` — приложение показывает «отправлено N продавцам».

**Массовый ответ** (`OfferService.bulkReply`) — один шаблон на N запросов в одной
транзакции: проверяется, что магазин действительно получал запрос и ещё не отвечал;
`replied_at` проставляется по каждому запросу, отсюда считается рейтинг скорости ответа.

**Протухание** — `ExpirationScheduler` раз в минуту переводит просроченные запросы в
`EXPIRED`, затем гасит висящие на них предложения. Срочный запрос живёт 2 часа, обычный — сутки.

**Идемпотентность** — аспект `IdempotencyAspect` над методами с `@Idempotent`
(`POST /requests`, `POST /offers`, `POST /offers/bulk`). Ключ уникален в пределах
пользователя (`userId:ключ`), ответ хранится в `idempotency_keys` сутки.

**Уведомления** — `OutboxDispatcher` каждые 5 секунд разгребает outbox. Если одному
продавцу в окно 15 минут накопилось больше трёх новых запросов, уходит одно
уведомление «8 новых запросов, 3 срочных» вместо восьми отдельных.

**Аналитика** — считается на лету из `request_recipients` + `offers` нативным SQL по
индексу `idx_recipients_store`, отдельного трекинга нет.

## Решения, которых не было в ТЗ явно

1. **Роль при регистрации.** `POST /auth/verify` создаёт пользователя с ролью `BUYER`
   (колонка `role` NOT NULL) и возвращает `isNewUser=true`; окончательная роль ставится
   в `POST /auth/register-role`. Для `SELLER` там же автоматически создаётся магазин —
   иначе весь кабинет `/my-store` был бы пустым. Повторный вызов → 409.
   Эндпоинт требует токен, выданный на шаге verify; после смены роли выдаётся новая пара токенов.
2. **Refresh-токен без таблицы** — stateless JWT с claim `typ=refresh` (access 15 мин, refresh 30 дней).
3. **Нативные enum-типы Postgres** маппятся как строки, а драйверу выставлен
   `stringtype=unspecified` — Postgres сам приводит значение к `user_role`/`request_status`/
   `offer_status`/`verification_status`. Схема из ТЗ не менялась.
4. **Лента продавца** отдаёт только активные запросы; пропущенные и отвеченные видны в аналитике.
   Группировка по категориям — на клиенте, как и указано в ТЗ.
5. **Телефоны филиалов** отдаются владельцу магазина и покупателю с принятой сделкой,
   остальным приходит `"phone": null`.
6. **Отзыв** можно оставить только после принятой сделки и только один на магазин;
   создаётся сразу со статусом `APPROVED`, поле `status` оставлено под будущую модерацию.
   Рейтинг магазина пересчитывается по одобренным отзывам.
7. **`null` в ответах не вырезаются** — клиент рассчитывает на `"repliedAt": null`.
8. **`GET /me/vehicles`** отдаёт машину по умолчанию первой; первая добавленная машина
   становится дефолтной автоматически.
9. Локально стоит JDK 22 — сборка идёт с `release 21`, как требует ТЗ.

## Чего нет в v1 (по §10 ТЗ)

Продажа автомобилей, карта и геопоиск, услуги СТО, онлайн-оплата и эскроу.
Схема под это заложена (`latitude`/`longitude`, `business_type`), эндпоинтов нет.

## Порядок разработки по ТЗ

Шаги 1–10 реализованы: скелет и миграции → SMS-вход и JWT → магазины и категории →
автомобили → запросы с веерной рассылкой → предложения, шаблоны и массовый ответ →
outbox и FCM → чаты → аналитика → отзывы и рейтинг.
