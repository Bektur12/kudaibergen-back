# Сверка BACKEND_SPEC (спецификация по дизайну) с кодом v2

Состояние на 29.09.2026, ветка `v2`. Отмечены только расхождения: что в спецификации сделано так же, здесь не повторяется.
Пункты 1–7 и 13–16 сделаны (миграции V9–V11) — отмечены ✅.

## 1. Меняют поведение — нужно решение

| # | Спецификация | Сейчас в коде | Рекомендация |
|---|---|---|---|
| 1 ✅ | Запрос живёт `duration` (30 мин / 1 ч / 3 ч / до конца дня, экран 06б), потом EXPIRED; продавец после срока не отвечает (409); покупатель продлевает до 3 раз (`POST /requests/{id}/extend`) | ТЗ: через 30 минут без «Есть» — экран «Пока никто не ответил», EXPIRED через 7 дней без действий | **Сделано.** `duration`, `expires_at`, `extended_times`; OPEN → ACTIVE; `POST /requests/{id}/extend`; таймер истечения вместо `no_reply_at` и 7 дней |
| 2 ✅ | Адресаты MARKET / ROWS (1–10 рядов) / CONTAINERS (1–30 контейнеров, **без фильтра по марке**), экран 31 | MARKET / ROW / SHOP — по одному ряду или боксу, марка фильтруется всегда | **Сделано.** `target_row_ids`, `target_container_ids`; счётчик — `GET /requests/estimate` → `{recipients, brand}` вместо `POST /requests/recipients-preview` |
| 3 ✅ | Статистика запроса для покупателя `GET /requests/{id}/stats`: delivered / seen / have / notHave / silent, списки «Есть» и «Нет» (только ряд и контейнер), живое обновление | Есть `recipientsCount`, `seenCount`, `haveCount` в карточке запроса | **Сделано.** `request_recipients.status`, `row_id`, `container_id`; `GET /requests/{id}/stats`; живое — событие `REQUEST_STATS` в `inbox:{userId}#{userId}` |
| 4 ✅ | Фото к запросу (до 3) и к ответу «Есть» | Нет: делались до модуля media | **Сделано.** `request_photos`, `reply_photos` (до 3), `mediaIds` в запросе и ответе, purpose REQUEST / REPLY |
| 5 ✅ | `GET /market/rows/{id}/containers?brandId=` с `state` HAS_SELLER / NO_SELLER и `sellsBrand`, счётчик «продают Toyota: 9» (экран 31) | `GET /market/rows/{id}` отдаёт контейнеры с магазином или null, без марки | **Сделано.** `GET /market/rows/{id}` и `/rows/{id}/containers` с `brandId`: `state`, `sellsBrand`, `brandSellers` |
| 6 ✅ | Подсветка рядов на карте `GET /market/map/highlight?kind=BRAND\|CATEGORY&id=` (ТЗ 6.1) | — | **Сделано.** Поля спецификации + `containerIds`, `openNowCount`, ближайший бокс по проходам от точки, GPS или входа |
| 7 ✅ | Статистика продавца `GET /seller/stats?period=` (ТЗ 12) | — | **Сделано.** `GET /my/shop/stats?period=WEEK\|MONTH` (модуль stats): поля спецификации + `answeredNotHave`, `buyersArrived`, `partViews` |
| 8 | In-app уведомления `Notification(…, readAt)` | Только пуши FCM, списка уведомлений нет | Уточнить, есть ли экран со списком уведомлений; если нет — не делать |

## 2. Формат и названия — дешёвые правки

| # | Спецификация | Сейчас | Комментарий |
|---|---|---|---|
| 9 | ID — UUID | BIGINT | Решено в BACKEND_DESIGN, раздел 0. Менять дорого, фронту всё равно |
| 10 | Пути `/seller/...`, `/garage/cars` | `/my/...`, `/me/cars` (`/requests/estimate` уже как в спецификации) | Можно добавить алиасы, если фронт уже пишется по спецификации |
| 11 | Загрузка фото: presign → PUT в MinIO → привязка | Multipart через бэкенд `POST /media/photos` | Сознательно: сервер пережимает фото и удаляет EXIF (ТЗ 14) |
| 12 | `PartStatus.OUT_OF_STOCK` | «Нет в наличии» = `quantity = 0` при ACTIVE | Фронту удобнее статус — можно отдавать вычисляемым полем |
| 13 ✅ | `side` детали: LEFT / RIGHT / **PAIR** | — | **Сделано** (V11) |
| 14 ✅ | `Brand.shortName` («Mercedes» в плитке) | — | **Сделано**: `brands.short_name`, `BrandDto.shortName`, ищется наравне с названием |
| 15 ✅ | Категории-енам с OTHER, FILTERS | Таблица `categories` | **Сделано**: «Другое» (`other`) последней; фильтры — «Фильтры и ТО» (`service`) |
| 16 ✅ | Подсказки «что нужно» `part_hint`, топ-3 для машины (06) | — | **Сделано**: `part_hints` (12 шт.), `GET /requests/hints?carId=`; `hintId` запроса задаёт категорию и учится на запросах по модели и марке |
| 17 | Ответ поиска: `currency`, `mainPhoto`, `appliedCar {brand, displayName, year}`, магазин плоско (`row`, `container`, `isOpenNow`) | `photo`, `carLabel`, магазин — `ShopCardDto` (location, open) | Данные те же, форма другая; подстроить при интеграции с фронтом |
| 18 | `Review.text` | Только звёзды и теги (как в ТЗ 4.5) | Уточнить по макету 09 |
| 19 | Живые события — STOMP | Centrifugo (решение ТЗ и BACKEND_DESIGN) | Оставить Centrifugo |
