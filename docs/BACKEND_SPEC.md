# Кудайберген — спецификация бэкенда по дизайну

Что нужно каждому экрану макета (`design/screens/*.png`) от API и как это устроено в бэкенде (`kudaibergen-back`, ветка `v2`). Стек: Java 21, Spring Boot 3, PostgreSQL, Redis, Centrifugo, MinIO.

Документ — **контракт для фронта**: пути, поля и енамы здесь совпадают с кодом. Полное описание всех полей — Swagger (`/swagger-ui.html`). Внутреннее устройство — `docs/BACKEND_DESIGN.md`, расхождения с первой версией спецификации и принятые решения — `docs/SPEC_GAPS.md`.

Обозначения: `?` — может быть null, `[NN]` — экран макета.

---

## 0. Общие правила

| тема | правило |
| --- | --- |
| Базовый путь | `/api/v1`. Покупатель — `/me/...`, `/requests/...`, `/parts/...`; продавец (свой бокс) — `/my/shop/...`, `/my/parts/...`. Путей `/seller/...` и `/garage/...` нет. |
| id | Числа (BIGINT), не UUID. Для ссылок «Поделиться» у магазина и запчасти есть отдельный непредсказуемый `publicId` (10 символов base62) — см. 6 и 4.4. |
| Вход | `Authorization: Bearer {accessToken}` (SMS-код → `POST /auth/otp/verify`). Каталог, карта, марки, категории, профиль магазина открыты гостю. |
| Язык | `Accept-Language: ru` или `ky`; в данных язык — `RU` / `KG`. |
| Ошибки | RFC 7807, `Content-Type: application/problem+json`: `{type, title, status, detail, code, errors?, …}`. `code` — стабильный UPPER_SNAKE (`REQUEST_EXPIRED`, `EXTEND_LIMIT`…), по нему ветвиться; `detail` — текст для показа как есть. Ошибки полей — `code: VALIDATION_ERROR` и `errors: [{field, message}]`. Дополнительные поля по коду: `retryAfter` (сек, 429, ещё и заголовок `Retry-After`), `attemptsLeft` (неверный SMS-код), `missing[]` (`PART_INCOMPLETE`). Общие коды: `UNAUTHORIZED` (401), `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `PAYLOAD_TOO_LARGE`, `INTERNAL_ERROR`. |
| Типы | Ответы отдают **все поля всегда**; в OpenAPI у схем ответов все поля `required`, а поле, которое бывает `null`, помечено `nullable: true` (ссылка на схему — `allOf` + `nullable`). Тела запросов: обязательные поля — `required`, остальные можно не передавать. Енамы — енамы и в схеме. |
| Идемпотентность | `Idempotency-Key` на `POST /requests`, `POST /requests/{id}/replies`, сообщения чата (`clientId`). Без сети клиент повторяет с тем же ключом. |
| Цена | Целые сомы, `currency: "KGS"`. Фронт форматирует «3 200 сом». |
| Время | ISO-8601 UTC. «12 мин назад», «9:38», «вчера» — на клиенте. Рабочие часы магазинов — по Бишкеку. |
| Место | Всегда отдельно код ряда (`row` / `rowCode`), номер контейнера (`container` / `number`) и `side`. |
| Машина | `brand {id, slug, name, shortName, logoUrl, placeholder, color, popular}` + подпись модели + год. |
| Счётчики | Все бейджи и чипы считает сервер. |
| Фото | Загрузка через бэкенд: `POST /media/photos` (multipart, `file`, `purpose`) → `{id, url, thumbUrl, width, height}`; дальше `id` передаётся в `mediaIds` сущности. **Видео** (только для заявки на услугу): `POST /media/videos` (multipart: `file` — видео до 30 с и 100 МБ, `poster?` — обложка-кадр, `durationSec?` — нужна только не для MP4/MOV, `purpose=SERVICE`) → `MediaItemDto {id, kind: VIDEO, url, thumbUrl?, width?, height?, durationSec}`. Ошибки 400: `VIDEO_TOO_LONG`, `DURATION_REQUIRED`, `BAD_MEDIA_TYPE`, `FILE_TOO_LARGE`, `VIDEO_NOT_ALLOWED`. Сервер пережимает в 1080 и 320 px и убирает EXIF. **Приложение само сжимает фото до 1080 px по длинной стороне (JPEG) перед отправкой** — на рынке слабый мобильный интернет; сервер принимает до 10 МБ. HEIC не принимается. |
| Живые события | **Centrifugo**, не STOMP. См. раздел 11. |

---

## 1. Енамы

```java
enum UserRole        { BUYER, SELLER, MASTER }                      // [03]; MASTER — мастер / СТО (раздел 12)
enum Lang            { RU, KG }                                     // [01, 19, 21]
enum Theme           { LIGHT, DARK, SYSTEM }                        // [19]

enum RowType         { ROW, VROW, STALL }                           // ряд / вертикальный ряд / жайма
enum Side            { NORTH, SOUTH, EAST, WEST }                   // «северная сторона» [10, 18, 22, 30]

enum ShopStatus      { PENDING_VERIFICATION, ACTIVE, BLOCKED, REJECTED }  // на проверке / работает / заблокирован / отклонён (и у мастера)

// Категории — таблица (GET /categories), не енам: suspension, brakes, engine, lights, body, electrics,
// cooling, transmission, interior, service («Фильтры и ТО»), other («Другое»).

enum PartCondition   { NEW, USED, ON_ORDER }                        // Новое / Б/У / Под заказ [12, 26, 29]
enum PartStatus      { DRAFT, ACTIVE, ARCHIVED }                    // «Черновик» [26], опубликована, в архиве
enum StockStatus     { IN_STOCK, OUT_OF_STOCK }                     // бейдж «В наличии» / «Нет» [24, 27, 29]; OUT_OF_STOCK = quantity 0
enum PartSide        { LEFT, RIGHT, PAIR }                          // «левая», «правая», «пара»
enum PartPosition    { FRONT, REAR }                                // «передняя», «задние»
enum PartSort        { PRICE_ASC, PRICE_DESC, NEWEST, NEAREST }     // «Дешевле ▾» [27]

enum RequestTarget   { MARKET, ROWS, CONTAINERS }                   // Всему рынку / Рядам / Контейнерам [06б, 31]
enum RequestDuration { MIN_30, HOUR_1, HOUR_3, END_OF_DAY }         // «Сколько ждать ответы» [06б], по умолчанию MIN_30
enum RequestStatus   { ACTIVE, EXPIRED, CLOSED }                    // идёт до expiresAt / время вышло / закрыт покупателем
enum RequestState    { WAITING, HAS_ANSWERS, NO_ANSWERS, EXPIRED, CLOSED } // бейдж [05, 07, 20] — см. 7.2
enum RecipientStatus { DELIVERED, SEEN, HAVE, NOT_HAVE, EXPIRED }   // у продавца [32]
enum ReplyAnswer     { HAVE, NOT_HAVE }                             // «Есть» / «Нет» [11, 14]
enum ReviewTag       { FAST_REPLY, PART_OK, EASY_TO_FIND }          // Быстро ответил / Деталь как надо / Легко найти бокс [09]

enum ContainerState  { HAS_SELLER, NO_SELLER }                      // [31]
enum MapFilterKind   { BRAND, CATEGORY }                            // «Марка / Запчасть» [15]
enum StatsPeriod     { WEEK, MONTH }                                // [17] — последние 7 / 30 дней

enum MediaPurpose    { PART, SHOP, AVATAR, REQUEST, REPLY, MASTER, SERVICE } // для POST /media/photos

// раздел 12: заявки на услуги
enum CarOrigin       { JAPAN, USA, EUROPE, KOREA, UAE, UNKNOWN }    // «японец / американец / европеец / кореец / эмиратец / не знаю» [35]
enum FuelType        { PETROL, DIESEL, LPG_PETROL, HYBRID, ELECTRIC } // [35]
enum ServiceWhen     { NOW, TODAY, TOMORROW, AT_TIME }              // [36]
enum ServiceWhere    { I_COME, MASTER_COMES, PICKUP_POINT }         // поеду сам / мастер выезжает / эвакуатор забирает [36]
enum ServiceDuration { MIN_15, MIN_30, HOUR_1, HOUR_3, END_OF_DAY } // по умолчанию — из справочника услуги (эвакуатор — 15 мин)
enum OfferAnswer     { CAN_HELP, NOT_MINE }                         // «Могу помочь» / «Не моё» [39]
enum ServiceRecipientStatus { DELIVERED, SEEN, CAN_HELP, NOT_MINE, EXPIRED }
enum ServiceRequestState { WAITING, HAS_OFFERS, NO_OFFERS, EXPIRED, CLOSED }
// ServiceType — справочник GET /service-types: CAR_REPAIR, TIRE_SERVICE, LPG, TOW_TRUCK, CAR_WASH, AUTO_ELECTRIC,
// BODY_PAINT, DIAGNOSTICS, OIL_CHANGE, WHEEL_ALIGNMENT, TINTING, MOBILE_MASTER
enum ChatSide        { BUYER, SHOP, SYSTEM }
enum MessageType     { TEXT, PHOTO, VOICE, VIDEO, REPLY, PART, QUICK, SYSTEM }
enum QuickReply {                                                   // быстрые ответы [08, 13]
    ROUTE_TO_BOX,    // Как пройти к боксу       (покупатель, действие клиента → [18])
    CLOSE_REQUEST,   // Купил — закрыть запрос    (покупатель, действие клиента → [09])
    ARRIVED,         // Я на месте                (покупатель, сообщение)
    RESERVED,        // Отложил для вас           (продавец)
    ROUTE,           // Как пройти                (продавец, карточка с местом)
    SOLD             // Продано                   (продавец)
}
```

**Типы пушей** (`data.type`), куда ведут:

| type | кому | куда |
| --- | --- | --- |
| `NEW_REQUEST` | людям бокса | [12]; category `NEW_REQUEST` — кнопки «Есть / Нет» [14] |
| `REPLY_HAVE` | покупателю | [07] |
| `NO_REPLY` | покупателю | **[20] «Пока никто не ответил»**: время вышло, «Есть» ноль |
| `REQUEST_EXPIRED` | покупателю | [32]: время вышло, ответы есть — «Продлить?» |
| `SALE` | людям бокса | запрос закрыт «Купил» у этого бокса |
| `CHAT_MESSAGE` | собеседнику | [08] / [13] |
| `PRICE_DROP`, `OUT_OF_STOCK` | у кого запчасть в избранном | [29] |
| `NEW_SERVICE_REQUEST` | мастеру | [39]; category `NEW_SERVICE_REQUEST` — кнопки «Могу помочь / Не моё» |
| `SERVICE_OFFER` | клиенту | [37] — мастер ответил «Могу помочь» |
| `SERVICE_NO_OFFERS` | клиенту | [37] время вышло без откликов — «Расширить радиус» |
| `SERVICE_EXPIRED` | клиенту | [37] время вышло, отклики есть — «Продлить» |
| `SERVICE_DEAL` | мастеру | клиент выбрал его («Договорились») |
| `ADMIN_MESSAGE` | продавцу / мастеру | сообщение администрации (`kind`: `MESSAGE` или `WARNING` — предупреждение); текст в body, ответить нельзя |
| `ACCOUNT_STATUS` | людям бокса / мастеру | решение администрации: `target` `SHOP` / `MASTER`, `id`, `event` `APPROVED` / `REJECTED` / `BLOCKED` / `UNBLOCKED`; вести в «Мой бокс» / профиль мастера |
| `DISPUTE_RESOLVED` | обеим сторонам спора | спор за контейнер решён: `disputeId`, `won` `true` / `false` |
| `COMPLAINT_RESOLVED` | заявителю | жалоба рассмотрена: `complaintId`, `upheld` (меры приняты / нарушений нет) |

---

## 2. Справочники

**Версия справочников:** `GET /dictionaries/version` → `{version, updatedAt}` — растёт при правке марок, моделей, категорий, услуг и подсказок в админке; изменилась — перекачать справочники. Списки справочников отдают `ETag` (повтор с `If-None-Match` → 304). Скрытое в админке в списках не появляется.

### 2.1 Марки — `GET /brands?popular=&q=`, `GET /brands/{id}/models`, `GET /models?brandId=&q=`
`BrandDto {id, slug, name, shortName, logoUrl?, placeholder, color, popular}`. `shortName` — подпись в плитке [23, 28] («Mercedes»). `logoUrl = null` — круг с буквой `placeholder` цветом `color`. Поиск `q` — по названию, короткому имени и народным названиям («мерс», «бэха», «камри»).

18 марок. Популярные (8 плиток, в этом порядке): Toyota, Lexus, Mercedes-Benz, BMW, Honda, Nissan, Hyundai, Kia. Остальные — «Все марки А–Я».

Модель — модель + поколение: подпись `label` «Camry 50», «E-класс W211»; годы — для проверки года машины и фитментов.

### 2.2 Категории — `GET /categories`
`CategoryDto {id, slug, name}` на языке запроса, в порядке чипов: Ходовая · Тормоза · Двигатель · Оптика · Кузов · Электрика · Охлаждение · Трансмиссия · Салон · Фильтры и ТО · Другое.

### 2.3 Подсказки «что нужно» [06] — `GET /requests/hints?carId=&limit=3`
`[{id, text, categoryId}]` — «Колодки», «Стойки», «Фара»… Сначала то, что чаще спрашивали за 90 дней для этой модели, потом марки, дальше самые частые вообще. Выбранная подсказка передаётся в `hintId` запроса — категория запроса берётся из неё.

---

## 3. Пользователь и гараж

- `GET /me` → `{id, phone, name?, avatarUrl?, role, lang, adminRole?, onboarded, hasShop, shop? {id, name, status, role}, createdAt}` (тот же объект — `user` в ответе `POST /auth/otp/verify`). **Роль ещё не выбрана — `onboarded = false`** (в ответе входа это же — `isNewUser = true`): вести на [03], даже если человек уже входил и закрыл приложение на выборе роли. `role` до выбора — `BUYER` по умолчанию, по нему не ориентироваться. `hasShop = false` — продавца ведут на регистрацию [10а]; `hasMaster` / `master? {id, name, status}` — профиль мастера, нет — в режиме мастера вести на [38]; `shop.status` — `PENDING_VERIFICATION` / `ACTIVE` / `BLOCKED` / `REJECTED`, `shop.role` — `OWNER` / `STAFF`; `adminRole` — `SUPER_ADMIN` / `MARKET_ADMIN`, если пользователь сотрудник веб-админки (в приложении ничего не открывает, только признак); `PATCH /me {name?, lang?, avatarMediaId?}`; `DELETE /me/avatar`; `PUT /me/role {role}` [03].
- `GET/PATCH /me/settings` → `{notifyReplies, notifyChat, newRequestSound, theme}` [19, 21].
- `POST /devices {token, platform}` / `DELETE /devices/{token}` — FCM.
- Гараж [04]: `GET /me/cars`, `POST /me/cars {modelId, year, engine?, vin?, engineVolume?, fuel?, origin?, …}`, `PATCH /me/cars/{id}`, `POST /me/cars/{id}/primary`, `DELETE /me/cars/{id}`. Машина: `brand`, модель, `year`, `isPrimary`, `engineVolume?`, `fuel?`, `origin` (по умолчанию — по стране марки: Toyota — `JAPAN`); подпись «Camry 50 · 2012».
- Избранное: `GET /me/favorite-parts`, `GET /me/favorite-shops`.

---

## 4. Поиск запчастей [27, 28, 29, 30]

### 4.1 `GET /parts/search`

| параметр | откуда в UI |
| --- | --- |
| `q` | строка «стойки»; прощает опечатки, знает синонимы, ищет и по номеру детали |
| `carId` | чип машины из гаража (нужен вход) |
| `brandId`, `modelId?`, `year?` | «Любая марка ›» → плитка марки → чип модели [28] |
| `categoryId` | чипы «Все / Ходовая / Тормоза…» |
| `condition`, `priceMin`, `priceMax` | фильтры |
| `inStock` (по умолчанию `true`), `openOnly` | «только в наличии», «только открытые боксы» |
| `sort` (по умолчанию `PRICE_ASC`) | «Дешевле ▾»; `NEAREST` — с `x`, `y` точки схемы (иначе от главного входа) |
| `cursor`, `limit` (20) | бесконечная прокрутка |

Сначала точное совпадение модели, потом совпадение по марке, внутри — `sort`. Запчасти одного магазина — `GET /shops/{shopId}/parts` (те же фильтры, вкладка «Запчасти» [30]). «Показать 86» [28] — `GET /parts/search/count` (те же фильтры).

### 4.2 Ответ
```json
{
  "items": [{
    "id": 101,
    "title": "Стойка передняя KYB, левая",
    "price": 3200,
    "currency": "KGS",
    "condition": "NEW",
    "stockStatus": "IN_STOCK",
    "mainPhoto": { "id": 55, "url": "…", "thumbUrl": "…", "width": 1080, "height": 1080 },
    "fits": true,
    "exactModel": true,
    "isFavorite": false,
    "shop": { "id": 7, "name": "Автодеталь Азамат", "avatarUrl": "…", "rating": 4.8,
              "row": "14", "rowLabel": "Ряд 14", "container": 12, "side": "NORTH",
              "isOpenNow": true, "openTo": "17:00:00" }
  }],
  "total": 24,
  "appliedCar": { "brand": { "id": 1, "name": "Toyota", "shortName": "Toyota", "…": "…" },
                  "modelId": 3, "displayName": "Camry 50", "year": 2012, "label": "Camry 50 · 2012" },
  "nextCursor": "…"
}
```
- Заголовок «**24 запчасти** для Camry 50» = `total` + `appliedCar.displayName`. `appliedCar = null` — машина не выбрана.
- `fits = null` — машина не выбрана; `exactModel` — подходит именно к модели, а не «ко всем моделям марки».
- Главное фото везде называется `mainPhoto` (карточка, «Мои запчасти», карточка товара в чате).
- `total = 0` — «Не нашли? Спросите весь рынок» → [06] с `text = q` и `carId`.

### 4.3 Карточка [29] — `GET /parts/{id}?carId=|brandId=&modelId=&year=`
`PartDetailDto {id, publicId, status, title, price, condition, quantity, stockStatus, category, manufacturer?, oemNumber?, side?, position?, photos[], fitments[], fit? {carLabel, fits}, isFavorite, shop: ShopCardDto, shopPhotos[3], publishedAt, updatedAt}`. `fit` — «Подходит к вашей Camry 50 · 2012». Открытие — просмотр (не чаще раза в час на человека).
Избранное: `PUT` / `DELETE /parts/{id}/favorite`.

### 4.4 Ссылка «Поделиться»
- Запчасть: `https://kudaibergen.kg/p/{publicId}` → `GET /parts/public/{publicId}` (ответ как у `/parts/{id}`).
- Магазин: `https://kudaibergen.kg/s/{publicId}` → `GET /shops/public/{publicId}` (ответ как у `/shops/{id}`).
- В ссылках — только `publicId`, не числовой `id`.

---

## 5. Каталог продавца [24, 25, 26]

- `GET /my/parts?filter=ALL|IN_STOCK|OUT_OF_STOCK|DRAFT|ARCHIVED&q=&cursor=` → строки `{id, status, title, mainPhoto?, brands[], fitmentLabels[], price?, quantity, stockStatus, views}`.
- `GET /my/parts/summary` → чипы `{all, inStock, outOfStock, drafts, archived, viewsWeek, activeLimit}`: «Все · 38 / В наличии · 32 / Нет · 6», «посмотрели **214 раз** за неделю».
- `GET /my/parts/{id}` — форма редактирования.
- `POST /parts?publish=false` (черновик) → `PATCH /parts/{id}` → `POST /parts/{id}/publish`; `POST /parts/{id}/archive` / `restore`; `DELETE /parts/{id}` (владелец).
- Тело: `{title, categoryId, condition, price, quantity, manufacturer?, oemNumber?, side?, position?, mediaIds[] (1–6, первое — главное), fitments[{brandId, modelId?, yearFrom?, yearTo?}] (1–20)}`. Порядок фото = порядок `mediaIds`.
- Публикация требует: название 3–120, категорию, состояние, цену, 1–6 фото, 1–20 машин; иначе 400 `PART_INCOMPLETE` с `missing[]`. Не больше 500 опубликованных.
- Камера «Номер детали» [25]: `POST /ocr/oem` (multipart) → кандидаты номера.
- Импорт: `GET /my/parts/import/template`, `POST /my/parts/import` (.xlsx) → черновики и отчёт по строкам.

---

## 6. Магазин [10, 10а, 21, 22, 23, 30]

- Сетка выбора бокса [10а]: `GET /market/rows`, `GET /market/rows/{id}` — контейнеры по сторонам, `occupied` — серые.
- Регистрация: `POST /shops {containerId, name, brandIds[], categoryIds[], openFrom?, openTo?, workDays?}` → **сразу `ACTIVE`**: проверка места пока выключена (`SHOP_VERIFICATION_REQUIRED=false`), экран «На проверке» и сканирование QR не показывать, переезд (`POST /my/shop/relocation`) тоже сразу. Когда проверку включат, магазин будет `PENDING_VERIFICATION` до подтверждения: `GET /my/shop/verification` (`required`), `POST /my/shop/verification/qr {qrToken, lat?, lon?}`, SMS арендатора (`…/sms/send`, `…/sms/confirm`) или админ (`…/admin-request`).
- **Отклонён** (`status = REJECTED`, причина в `blockReason`): администрация не подтвердила место. Магазин скрыт и контейнер больше не держит. Показать причину и «Выбрать другой контейнер» → `POST /my/shop/relocation {containerId}` (можно и тот же): магазин снова уходит на проверку (или сразу действует, пока проверка выключена). У мастера то же: `master.status = REJECTED`, причина — в профиле.
- **«Это мой контейнер»**: `POST /market/containers/{id}/claim {text?}` → 201 — спор уходит администрации, ответ придёт пушем `DISPUTE_RESOLVED`. 409 `CONTAINER_FREE` — контейнер свободен, можно регистрироваться; 400 `OWN_CONTAINER` — там уже ваш магазин.
- `GET /my/shop`; `PATCH /my/shop {name?, openFrom?, openTo?, workDays?, phone?, phoneVisible?}`; `PUT /my/shop/brands {brandIds}` [23] («Сохранить · 4 марки»); `PUT /my/shop/categories {categoryIds}`.
- Тумблер «Бокс закрыт» [11, 21]: `PATCH /my/shop/open {isOpen}` — закрытым запросы не рассылаются.
- Аватар: `PUT /my/shop/avatar {mediaId}`, `DELETE /my/shop/avatar`. Фото места (до 8, первое — обложка): `GET/POST /my/shop/photos`, `PUT /my/shop/photos/order`, `POST /my/shop/photos/{mediaId}/cover`, `DELETE /my/shop/photos/{mediaId}`.
- Сотрудники [21]: `GET/POST /my/shop/members`, `DELETE /my/shop/members/{userId}`. Переезд: `POST/DELETE /my/shop/relocation`.
- Отзывы: `GET /my/shop/reviews`, `POST /my/shop/reviews/{id}/reply` (один раз). Шаблоны ответов: `/my/shop/reply-templates`.
- Публичный профиль [30]: `GET /shops/{id}` → `{id, publicId, name, avatarUrl?, rating, reviewsCount, location {rowId, rowCode, rowLabel, containerId, number, side}, open {openNow, closedManually, openFrom, openTo, workDays, opensAt?}, brands[], categories[], phone?, isFavorite, photos[], counts {parts, photos, reviews}}`. Ещё `GET /shops/{id}/photos`, `GET /shops/{id}/reviews`, `PUT/DELETE /shops/{id}/favorite`, список «Списком» — `GET /shops?brandId=&categoryId=&rowId=&q=`.

---

## 7. Запросы [05–07, 06б, 09, 11, 12, 20, 31, 32]

### 7.1 Отправка [06, 06б, 31]
- Живой счётчик: `GET /requests/estimate?carId=|brandId=&target=&rowIds=&containerIds=` → `{recipients: 18, brand: "Toyota"}` — «Запрос получат 18 продавцов…». Считается тем же правилом, что и рассылка.
- Контейнеры ряда для экрана 31: `GET /market/rows/{id}/containers?brandId=` → у контейнера `state: HAS_SELLER|NO_SELLER`, `sellsBrand`; у ряда `brandSellers` — «продают Toyota: 9».
- `POST /requests` (Idempotency-Key):
```json
{ "carId": 12, "text": "Стойки передние, пара", "categoryId": null, "hintId": 2,
  "target": "ROWS", "targetRowIds": [5, 6], "targetContainerIds": null,
  "duration": "HOUR_1", "mediaIds": [81] }
```
  Текст 3–200; фото до 3 (`purpose=REQUEST`); не больше 10 активных запросов (409 `OPEN_REQUESTS_LIMIT`) и 20 новых за сутки (429 `DAILY_REQUESTS_LIMIT`).
- **Кому уходит:** всегда только магазины `ACTIVE`, не закрытые тумблером, в рабочее время, не свой бокс. `MARKET` — у кого марка машины в марках магазина; `ROWS` (1–10 рядов) — то же, в выбранных рядах; `CONTAINERS` (1–30) — выбранные боксы **без фильтра по марке**.
- **Срок:** `expiresAt` = создание + `duration`; `END_OF_DAY` — до 17:00 по Бишкеку, если уже позже — 3 часа.

### 7.2 Покупатель
- `GET /requests/my?status=&cursor=` [05] → `{id, text, carLabel, status, state, haveCount, createdAt, expiresAt}`. Бейдж по `state`: `HAS_ANSWERS` — «N ответили» (зелёный), `WAITING` — «Ждём ответов», `NO_ANSWERS` — время вышло без «Есть» → [20], `EXPIRED` — время вышло, ответы есть, `CLOSED` — «Закрыт».
- `GET /requests/{id}` → `{id, text, car, category?, photos[], target, targetRowIds[], targetContainerIds[], status, state, duration, expiresAt, extendedTimes, canExtend, recipientsCount, seenCount, haveCount, closedWithShopId?, createdAt, closedAt?}`.
- `GET /requests/{id}/replies?afterId=` [07] — только «Есть», по времени: `{id, shop: ShopCardDto, condition, message?, price?, photos[], part?, chatId, createdAt}`.
- Статистика [32]: `GET /requests/{id}/stats`
```json
{ "requestId": 5, "status": "ACTIVE", "expiresAt": "…", "durationMin": 30, "remainingMin": 18,
  "canExtend": true, "extendedTimes": 0,
  "counts": { "delivered": 18, "seen": 14, "have": 3, "notHave": 7, "silent": 4 },
  "have":    [{ "shop": { "id": 7, "name": "…", "avatarUrl": "…", "…": "…" }, "row": "14", "container": 12,
                "answeredAt": "…", "price": 3200, "chatId": 31 }],
  "notHave": [{ "row": "14", "container": 1 }, { "row": "Ц", "container": 3 }] }
```
  `silent` = delivered − have − notHave (включая посмотревших). «Нет» — только ряд и контейнер. Видит только автор запроса (чужой — 404). Живое обновление — событие `REQUEST_STATS` (раздел 11).
- **Время вышло:** раз в минуту запросы с прошедшим `expiresAt` становятся `EXPIRED`, покупателю пуш: без «Есть» — `NO_REPLY` → [20] с кнопками «Отправить всему рынку» и «Закрыть запрос»; с ответами — `REQUEST_EXPIRED` → [32] «Продлить?».
- `POST /requests/{id}/extend {minutes: 30|60|180}` — до 3 раз (409 `EXTEND_LIMIT`); активному сдвигает срок, истёкший снова активен.
- `POST /requests/{id}/widen {target: "MARKET"}` [20, 32] — новым боксам марки пуш, срок заново → `{recipientsAdded, recipientsCount, expiresAt}`.
- `POST /requests/{id}/close {shopId?, stars?, tags[]?}` [09] — можно и после истечения; оценка только боксу, ответившему «Есть». Теги — `GET /reviews/tags`. Текста в отзыве нет.

### 7.3 Продавец [11, 12, 14]
- `GET /my/shop/requests?filter=NEW|ANSWERED|EXPIRED|UNANSWERED&cursor=` → `{id, text, car, category?, photos[], buyerName?, status, soldHere, createdAt, notifiedAt, expiresAt, seen, myReply?}`. `NEW` — активные без ответа (таймер «осталось 12 мин» по `expiresAt`); `EXPIRED` — «Истёкшие»; `UNANSWERED` — пропущенные (время вышло или закрыт, ответа не было).
- `GET /my/shop/requests/{id}`; `POST /my/shop/requests/{id}/seen` — продавец открыл карточку или нажал на пуш.
- `GET /my/shop/requests/{id}/suggested-parts` — свои запчасти под машину для «Приложить товар».
- `POST /requests/{id}/replies {answer, condition?, message?, price?, partId?, mediaIds[]≤3}` (purpose `REPLY`). «Есть» требует `condition`. После `expiresAt` — 409 `REQUEST_EXPIRED`. Изменить — `PATCH /requests/{id}/replies/mine` в течение 10 минут.

---

## 8. Чат [08, 13, 16]

- **Чат один на двоих:** у покупателя с магазином и у покупателя с мастером — ровно один чат, откуда бы его ни открыли (запрос, заявка, профиль, карточка товара). `POST /chats {shopId? | masterId?, requestId?, serviceRequestId?, partId?}` — открыть (одно из `shopId` / `masterId`); нет чата — создаётся, есть — тот же `id`. Ответ «Есть» и отклик «Могу помочь» приходят в этот же чат карточкой `REPLY` (в `payload` — `requestId` / `serviceRequestId`, по нему подписать «по запросу …»); `chat.request` / `chat.serviceRequest` — последний запрос или заявка, о которых шла речь.
- У чата с мастером в `GET /chats/{id}`: `shop = null`, `master` — карточка мастера, `serviceRequest` — закреплённая заявка; сторона мастера в сообщениях — `SHOP`. Отклик приходит первым сообщением `REPLY` с `payload {offerId, priceFrom, availableAt}`. Быстрых ответов в чате с мастером нет.
- `GET /chats?as=BUYER|SHOP|MASTER&cursor=` [16] — строки `{id, mySide, title, avatarUrl?, subtitle, requestId?, serviceRequestId?, masterId?, requestClosed, lastMessage?, unread, online, blocked, updatedAt}`; `GET /chats/unread` → `{asBuyer, asShop, asMaster}`.
- **Пожаловаться** (на запчасть, магазин, фото места, отзыв, сообщение, чат, мастера, отклик): `POST /complaints {type: PART|SHOP|SHOP_PHOTO|REVIEW|MASTER_REVIEW|CHAT|CHAT_MESSAGE|MASTER|SERVICE_OFFER, targetId, reason: FAKE_ORIGINAL|REVIEW_WITHOUT_PURCHASE|SPAM_FRAUD|WRONG_PLACE|RUDE|OTHER, text?, relatedRequestId?, relatedServiceRequestId?}` → 201. Повтор на то же не дублируется; 404 `COMPLAINT_TARGET_NOT_FOUND`, 429 `COMPLAINTS_RATE_LIMITED`. Скрытое администрацией в приложении не показывается: запчасть уходит в архив (публикация — 409 `PART_HIDDEN`), сообщение приходит без текста и вложений с `code = HIDDEN_BY_ADMIN` — показать «Сообщение скрыто администрацией».
- `GET /chats/{id}`, `GET /chats/{id}/messages?cursor=`, `POST /chats/{id}/messages {text?|quick?, clientId}`, `POST /chats/{id}/messages/media` (multipart: `file`, `type` = `PHOTO` / `VOICE` / `VIDEO`, `durationSeconds` — для голосового и видео, `clientId`; фото до 10 МБ, голосовое до 60 с, видео до 100 МБ), `POST /chats/{id}/read`. В FormData у файла передавать `name` с расширением и `type` (`image/jpeg`, `video/mp4`, `audio/m4a`…); если тип не передан или `application/octet-stream`, сервер определит его по содержимому. **Ссылки на файлы** (`mediaUrl`, `url`, `thumbUrl`) приходят полными (`http://<адрес сервера>/media/…`) — открывать как есть.
- `GET /chats/{id}/quick-replies` — быстрые ответы для своей стороны.
- `PUT/DELETE /chats/{id}/block`, `POST /chats/{id}/complaints`.
- Живая доставка — Centrifugo, раздел 11.

---

## 9. Карта и маршрут [15, 18]

- `GET /market/map` (ETag `"map-v{version}"`, с `If-None-Match` — 304) → граница, ряды, проходы, входы, POI, матрица GPS.
- `GET /market/search?q=` — «14», «ряд ю», «14 12». `GET /market/qr/{token}` — QR ряда или контейнера → точка схемы. `POST /market/locate {lat, lon}` → точка.
- Подсветка [15]: `GET /market/map/highlight?kind=BRAND|CATEGORY&id=&fromX=&fromY=|fromLat=&fromLon=|fromEntrance=` →
  `{kind, id, name, rowIds[], containerIds[], shopsCount, rowsCount, openNowCount, nearestRowId?, nearestRow?, nearestContainerId?, nearestContainer?, nearestShopId?, nearestDistanceM?, nearestMinutes?, fromSource}` — «Mercedes-Benz — в 6 рядах · 21 бокс · ближайший ряд 14, 170 м».
- Маршрут [18]: `GET /market/route?toContainerId=&fromX=&fromY=|fromLat=&fromLon=|fromEntrance=` → `{target, containerPoint, from, fromSource, polyline[[x,y]], distanceM, minutes, steps[]}`. Без точки и GPS — от главного входа.

---

## 10. Статистика бокса [17]

`GET /my/shop/stats?period=WEEK|MONTH` (последние 7 / 30 дней):
```json
{ "period": "WEEK", "from": "…", "to": "…",
  "requestsByBrands": 128, "answeredHave": 46, "answeredNotHave": 40, "wroteInChat": 31, "buyersArrived": 12,
  "sales": 19, "unanswered": 22, "avgReplyMinutes": 4, "partViews": 214,
  "topCategories": [{ "categoryId": 1, "name": "Ходовая", "count": 24 }] }
```
`unanswered` — пропущенные (время вышло или закрыт, ответа не было); «Смотреть» → `GET /my/shop/requests?filter=UNANSWERED`. `sales` — запросы, закрытые «Купил» у бокса. `topCategories` — до 4, по запросам с выбранной категорией.

---

## 11. Живые события — Centrifugo

Не STOMP. Клиент — официальный SDK Centrifugo (`centrifuge-js` / `centrifuge` для React Native), протокол WebSocket.

1. `GET /realtime/token` → `{token, expiresInSeconds}` — токен подключения; SDK обновляет его через `getToken`-коллбэк тем же запросом.
2. Сразу после подключения — подписка на **личный канал** `inbox:{userId}#{userId}` (без отдельного токена; Centrifugo пускает туда только этого пользователя). Presence на нём = «в сети».
3. Открыли чат — подписка на `chat:{chatId}` с токеном `GET /chats/{id}/subscription-token`; закрыли — отписка.

Все события — конверт `{type, payload}`. Поля в `payload` — те же, что в REST, и в том же формате: даты строками ISO 8601 (`"2026-10-01T04:33:27.667Z"`, время — `"09:00:00"`).

| канал | type | payload |
| --- | --- | --- |
| `inbox:{userId}#{userId}` | `CHAT` | обновлённая строка списка чатов [16] |
| `inbox:{userId}#{userId}` | `UNREAD` | `{asBuyer, asShop, asMaster}` — бейджи непрочитанных |
| `inbox:{userId}#{userId}` | `REQUEST_STATS` | статистика своего запроса — как `GET /requests/{id}/stats` [32] |
| `inbox:{userId}#{userId}` | `SERVICE_REQUEST_STATS` | статистика своей заявки на услугу — как `GET /service-requests/{id}/stats` [37] |
| `chat:{chatId}` | `MESSAGE` | сообщение |
| `chat:{chatId}` | `READ` | `{chatId, side, readMessageId}` |
| `chat:{chatId}` | `TYPING` | публикует сам клиент |

Источник истины — REST: после переподключения клиент перечитывает открытый экран (история каналов 10 минут догоняет короткие обрывы).

---

## 12. Заявки на услуги и роль «Мастер» [05б, 33–39]

Отдельный поток рядом с запросами на запчасти: клиент описывает проблему с машиной, заявку получают мастера нужной услуги, которые работают с этой маркой и страной машины и находятся рядом; они отвечают «Могу помочь» или «Не моё». Время, продление и статистика — как у запросов (раздел 7).

### 12.1 Справочник услуг — `GET /service-types`
`[{code, name, icon, needsLocation, urgent, defaultDuration}]` в порядке плиток [33, 38]. `needsLocation` — заявке нужна точка на карте (эвакуатор, выездной мастер); `urgent` — «Срочно» (эвакуатор, 15 минут).

### 12.2 Профиль мастера [38]
- `POST /my/master` → стать мастером, пользователь переходит в режим `MASTER`:
```json
{ "name": "СТО «Ходовик»", "services": ["CAR_REPAIR", "DIAGNOSTICS"], "allBrands": false, "brandIds": [1, 2],
  "origins": ["JAPAN", "KOREA"], "address": "ул. Садыгалиева 41, бокс 3", "lat": 42.845, "lng": 74.625,
  "radiusKm": 5, "mobile": false, "openFrom": "09:00", "openTo": "19:00", "workDays": ["TUESDAY", "…"], "phone": "+996…" }
```
  `origins` пусто — любые машины; `allBrands` — все марки; радиус 1–30 км (по умолчанию 5). Проверка мастера — как у магазинов: сейчас выключена, профиль сразу `ACTIVE`.
- `GET /my/master` (нет — 404 `NO_MASTER` → вести на [38]), `PATCH /my/master` (null — не менять), `PATCH /my/master/accepting {accepting}` — «● Принимаю», `PUT/DELETE /my/master/avatar {mediaId}`, `PUT /my/master/photos {mediaIds}` (до 8, первое — обложка, `purpose=MASTER`).
- Для клиента: `GET /masters/{id}` → `{id, publicId, name, avatarUrl?, rating, reviewsCount, services[], allBrands, brands[], origins[], address, lat, lng, radiusKm, mobile, open {openNow, …}, phone?, photos[]}`; `GET /masters/public/{publicId}` — ссылка «Поделиться»; `GET /masters/{id}/reviews`.
- Один человек может быть и продавцом, и мастером. Вкладки мастера: Заявки · Чаты (`GET /chats?as=MASTER`) · Статистика · Профиль.

### 12.3 Заявка клиента [33–37]
- `GET /service-requests/estimate?service=&carId=|brandId=&origin=&lat=&lng=&radiusKm=` → `{recipients: 12, label: "СТО и ремонт · Toyota"}` [36].
- `POST /service-requests` (Idempotency-Key):
```json
{ "service": "TOW_TRUCK", "carId": 12, "description": "Не заводится, стартер щёлкает",
  "mediaIds": [81], "when": "NOW", "atTime": null, "where": "PICKUP_POINT",
  "lat": 42.851, "lng": 74.63, "address": "ул. Ахунбаева 98", "radiusKm": 5, "duration": null }
```
  Машина — `carId` из гаража или `brandId` + `modelId?` + `year?` + `engineVolume?` + `fuel?` + `origin?` (страна по умолчанию — по марке). Описание 10–500 символов, до 5 фото и видео вместе (`purpose=SERVICE`; видео до 30 с — `POST /media/videos`). `atTime` — только при `when = AT_TIME`. `lat/lng` обязательны всегда: от этой точки считается расстояние до мастеров. Некому отправить — 409 `NO_RECIPIENTS`.
- **Кому уходит:** мастер `ACTIVE`, «Принимаю» включено, сейчас его рабочее время, услуга в его списке, марка — в его марках (или «Все марки»), страна — в его списке (или список пуст, или «не знаю»), расстояние от мастера до точки клиента ≤ меньшего из радиусов заявки и мастера.
- Вложения заявки: `photos[]` — только фото (как раньше), `media[]` — всё по порядку, фото и видео: `MediaItemDto {id, kind: PHOTO|VIDEO, url, thumbUrl?, width?, height?, durationSec?}`. У видео без обложки `thumbUrl = null` — показывать первый кадр.
- `GET /service-requests/my?cursor=` [05б] → `{id, service, description, carLabel, status, state, canHelpCount, createdAt, expiresAt}`. `state`: `WAITING` / `HAS_OFFERS` — активна, `NO_OFFERS` — время вышло без откликов (предложить «Расширить радиус»), `EXPIRED` — время вышло, отклики есть, `CLOSED`.
- `GET /service-requests/{id}` → `{…, service, car {brand, modelLabel?, year?, engineVolume?, fuel?, origin, label}, photos[], media[], when, atTime?, where, lat, lng, address?, radiusKm, status, state, duration, expiresAt, extendedTimes, canExtend, canWiden, recipientsCount, seenCount, canHelpCount, closedWithMasterId?, urgent}`.
- `GET /service-requests/{id}/offers?afterId=` [37] — отклики «Могу помочь» по времени: `{id, master {id, name, avatarUrl?, rating, reviewsCount, address, mobile, isOpenNow, phone?}, priceFrom?, availableAt?, message?, distanceM, chatId}` — «★ 4.9 · 1,2 км · от 1 500 сом · сегодня 15:00», «Позвонить», «Написать».
- `GET /service-requests/{id}/stats` → `{status, expiresAt, durationMin, remainingMin, canExtend, extendedTimes, radiusKm, counts {delivered, seen, canHelp, notMine, silent}}` — «Получили / Посмотрели / Могут / Не их профиль». Живое — `SERVICE_REQUEST_STATS` (раздел 11).
- `POST /service-requests/{id}/extend {minutes: 30|60|180}` — до 3 раз; `POST /service-requests/{id}/widen` — радиус +5 км (до 50), новым мастерам пуш, срок заново; `POST /service-requests/{id}/close {masterId?, stars?, tags[]?}` — «Договорились» (оценка только откликнувшемуся мастеру).

### 12.4 Мастер [39]
- `GET /my/master/requests?filter=NEW|ANSWERED|EXPIRED&cursor=` → `{id, service, car, description, photos[], media[], when, atTime?, where, address?, lat, lng, distanceM, buyerName?, status, dealHere, urgent, createdAt, notifiedAt, expiresAt, seen, myOffer?}` — «Шиномонтаж · Toyota Camry 50 · 1,2 км · осталось 12 мин».
- `GET /my/master/requests/{id}`, `POST /my/master/requests/{id}/seen`.
- `POST /my/master/requests/{id}/offer {answer: CAN_HELP|NOT_MINE, priceFrom?, availableAt?, message?}` (Idempotency-Key) — один раз; после срока — 409 `REQUEST_EXPIRED`, повтор — 409 `ALREADY_ANSWERED`. «Могу помочь» → клиенту пуш и чат (`chatId` в ответе).
- `GET /my/master/stats?period=WEEK|MONTH` → `{received, canHelp, notMine, wroteInChat, deals, unanswered, avgReplyMinutes?, topServices[{code, name, count}]}`.
