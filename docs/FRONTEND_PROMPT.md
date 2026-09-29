# Промпт: подключение фронта «Кудайберген» к бэкенду

Вставить в Claude Code в проекте фронта целиком.

---

Стек: Expo 57 (expo-router), React Native 0.86, TypeScript, NativeWind, TanStack Query, Zustand. Все 31 экран уже свёрстаны по макету на демо-данных из `src/mocks/index.ts`.

ЗАДАЧА: подключить приложение «Кудайберген» к настоящему бэкенду — заменить моки на API, сделать вход, живые события и загрузку фото. **Вёрстку не менять**: после подключения каждый экран должен выглядеть так же, как `design/screens/<экран>.png`.

ИСТОЧНИКИ:
- Контракт API — ниже в этом же промпте, раздел «КОНТРАКТ API». Он главный: пути, поля, енамы, коды ошибок, каналы Centrifugo, типы пушей.
- Swagger запущенного бэкенда: `http://localhost:8080/swagger-ui.html`, схема — `http://localhost:8080/v3/api-docs`. Если контракт и Swagger расходятся — верь Swagger и запиши расхождение в `BACKEND_QUESTIONS.md`.
- Вёрстка — как и раньше: `design/screens`, `design/specs`, `design/html`.

БЭКЕНД ЛОКАЛЬНО:
- в папке бэкенда `docker compose up -d` — Postgres, Redis, Centrifugo (порт 8000), MinIO.
- Приложение — на `localhost:8080` (запускает разработчик из IntelliJ; если не запущено — попроси).
- `EXPO_PUBLIC_API_URL`: iOS-симулятор и web — `http://localhost:8080`, Android-эмулятор — `http://10.0.2.2:8080`, телефон — IP компьютера в сети. Centrifugo: тот же хост, `ws://<host>:8000/connection/websocket` (`EXPO_PUBLIC_CENTRIFUGO_URL`). Добавь `.env.example`.
- Вход в dev: `POST /api/v1/auth/otp/send {phone}` возвращает код в поле `debugCode` — SMS не нужна. Номера только `+996…`.
- Бэкенд не правь. Чего-то не хватает или неудобно — запиши в `BACKEND_QUESTIONS.md` (экран, что нужно, почему) и продолжай с временным решением, помеченным `// TODO(backend)`.

АРХИТЕКТУРА (сначала это, потом экраны):
1. **Типы из OpenAPI.** `openapi-typescript` (devDependency) → `src/api/schema.ts`, скрипт `npm run api:types` берёт `/v3/api-docs`. Руками DTO не описывать; удобные алиасы — в `src/api/types.ts`.
2. **`src/lib/api.ts`** — доработать существующую обёртку:
   - базовый путь `/api/v1`, заголовок `Accept-Language: ru|ky` из языка пользователя;
   - ошибки — `ApiError {status, code, message, fields?, retryAfter?}` из тела `{code, message, fields}`; экраны показывают `message` и ветвятся по `code`, не по тексту;
   - токены в `expo-secure-store`; на 401 — один `POST /auth/refresh {refreshToken}` (single-flight: параллельные запросы ждут один refresh), потом повтор; refresh не удался — выход на экран 01;
   - `idempotencyKey` в опциях → заголовок `Idempotency-Key` (для `POST /requests`, `POST /requests/{id}/replies`);
   - `uploadPhoto(uri, purpose)` → multipart `POST /media/photos` → `{id, url, thumbUrl}`.
3. **Запросы по доменам** — `src/api/<домен>.ts`: хуки TanStack Query и фабрика ключей (`auth`, `me`, `garage`, `directory`, `requests`, `incoming`, `shop`, `parts`, `catalog`, `chats`, `market`, `stats`). Экраны не зовут `apiFetch` напрямую. Списки с `cursor` — `useInfiniteQuery`.
4. **Форматирование** — `src/lib/format.ts`: цена «3 200 сом» (целые сомы, неразрывный пробел), время «12 мин назад» / «9:38» / «вчера», место «Ряд 14 · Бокс 12» из `row`/`rowLabel` + `container`, машина «Camry 50 · 2012». Строки — как в макете.
5. **Моки.** Экран переходит на API — его импорты из `src/mocks` удаляются. Типы моков не тянуть в API-слой. В конце `src/mocks` удалить (или оставить только для скриншот-тестов, явно помеченным).

ПРАВИЛА ДАННЫХ:
- Счётчики, бейджи и «подходит / не подходит» не считать на клиенте — всё приходит с сервера (`total`, `appliedCar`, `stockStatus`, `summary`, `counts`, `state`, `recipients`, `canExtend`, `remainingMin`…).
- Бейдж запроса — по `state` (`WAITING`, `HAS_ANSWERS`, `NO_ANSWERS`, `EXPIRED`, `CLOSED`), наличие — по `stockStatus`, главное фото — `mainPhoto`.
- id — числа. В ссылках «Поделиться» — только `publicId`: `https://kudaibergen.kg/s/{publicId}` (магазин), `/p/{publicId}` (запчасть); открытие ссылки — `GET /shops/public/{publicId}`, `GET /parts/public/{publicId}` (настроить deep link в expo-linking).
- Фото перед загрузкой сжимать до 1080 px по длинной стороне, JPEG ~0.8 (`expo-image-manipulator`); HEIC не отправлять. Показывать прогресс и повтор при ошибке.
- Пустые состояния, загрузка и ошибка сети — в стиле макета (экран 20 «нет сети»), без стандартных спиннеров на весь экран, где в макете их нет.
- Даты с сервера — UTC ISO; показывать в местном времени.

ЖИВЫЕ СОБЫТИЯ — Centrifugo (не STOMP, не свой WebSocket):
- SDK `centrifuge`. Токен — `GET /api/v1/realtime/token`, он же в `getToken` для продления.
- Одно подключение на сессию; сразу подписка на `inbox:{userId}#{userId}`: `CHAT` → обновить строку в кэше списка чатов, `UNREAD` → бейджи вкладок, `REQUEST_STATS` → положить payload в кэш `GET /requests/{id}/stats`.
- Открытый чат — `chat:{chatId}` с токеном `GET /chats/{id}/subscription-token`: `MESSAGE`, `READ`, `TYPING` (TYPING публикует сам клиент). Ушли с экрана — отписаться.
- После переподключения — `invalidateQueries` для открытого экрана: REST — источник истины.

ПУШИ (последний этап):
- Токен устройства → `POST /api/v1/devices {token, platform}`, при выходе — `DELETE /devices/{token}`. Бэкенд шлёт через FCM: на Android это токен `getDevicePushTokenAsync`, для iOS нужен FCM-токен — выбери способ и запиши в `BACKEND_QUESTIONS.md`, если нужен APNs.
- Навигация по `data.type`: `NEW_REQUEST` → 12 (кнопки «Есть / Нет» — category `NEW_REQUEST`, ответ сразу `POST /requests/{id}/replies`), `REPLY_HAVE` → 07, **`NO_REPLY` → 20** («Отправить всему рынку» / «Закрыть запрос»), `REQUEST_EXPIRED` → 32, `CHAT_MESSAGE` → чат, `SALE` → 11, `PRICE_DROP` / `OUT_OF_STOCK` → 29.

ПОРЯДОК (после каждого шага — проверка, короткий отчёт, потом дальше):
1. Архитектура: типы, `api.ts`, refresh, SecureStore, `format.ts`, провайдер Centrifugo (пока без подписок).
2. Вход 01–03: `otp/send` → `otp/verify` (`isNewUser`, `user`) → `PUT /me/role`; при старте — `GET /me` по сохранённому токену.
3. Покупатель: 04 гараж (`/me/cars`, марки и модели), 05 главная (`/requests/my`), 06 «Найти запчасть» (подсказки `/requests/hints`, фото, `/requests/estimate` при каждом изменении выбора, `POST /requests`), 07 ответы, 09 закрытие, 20.
4. Экраны без макета — **06б** (кому отправить: весь рынок / ряды / контейнеры + «Сколько ждать ответы»), **31** (выбор контейнеров ряда: `GET /market/rows/{id}/containers?brandId=`, «продают Toyota: 9», предупреждение, если марки нет), **32** (статистика запроса, таймер, «Продлить», «Отправить всему рынку»). Собрать из существующих компонентов и токенов в стиле 06 и 07; содержание — раздел 7.1–7.2 контракта ниже. Сделать и показать мне скриншоты до перехода дальше.
5. Продавец: 10а, 10, 22, 23 (регистрация, проверка по QR, фото места, марки), 11 и 12 (лента `filter=NEW|ANSWERED|EXPIRED|UNANSWERED`, `…/seen` при открытии, ответ «Есть» с фото, таймер по `expiresAt`), 21, тумблер «Бокс закрыт».
6. Каталог: 24–26 (мои запчасти, камера и OCR номера `POST /ocr/oem`, черновик → публикация, ошибка `PART_INCOMPLETE` с `missing[]`), 27–30 (поиск с бесконечной прокруткой, выбор марки, «Показать N», карточка, профиль продавца, избранное).
7. Чаты 08, 13, 16 + подписки Centrifugo, быстрые ответы `GET /chats/{id}/quick-replies`.
8. Карта 15 и 18: схема — `GET /market/map` с ETag (кэш на устройстве, `src/data/market-map.json` — только запасной вариант без сети), подсветка `GET /market/map/highlight`, маршрут `GET /market/route`, QR ряда/контейнера `GET /market/qr/{token}`.
9. Статистика 17, профиль 19 и настройки, выход.
10. Пуши и deep links.

ПРОВЕРКА ПОСЛЕ КАЖДОГО ЭКРАНА:
- Экран работает на настоящем бэкенде: данные, пустое состояние, ошибка (выключи бэкенд), для списков — вторая страница.
- Скриншот 390×844 @2x на данных, похожих на демо, — сравнение с `design/screens/<экран>.png`: вёрстка не поехала (тексты с сервера могут отличаться — сравнивай геометрию).
- `npm run lint` и `tsc --noEmit` без ошибок.
- Коротко: что подключено, какие эндпоинты, что записано в `BACKEND_QUESTIONS.md`.

Начни с шага 1, покажи `api.ts`, пример доменного файла (`src/api/requests.ts`) и `format.ts`, затем переходи ко входу.

---

## КОНТРАКТ API

Обозначения: `?` — может быть null, `[NN]` — экран макета. Базовый путь всех эндпоинтов — `/api/v1`.

### 0. Общие правила

| тема | правило |
| --- | --- |
| Базовый путь | `/api/v1`. Покупатель — `/me/...`, `/requests/...`, `/parts/...`; продавец (свой бокс) — `/my/shop/...`, `/my/parts/...`. Путей `/seller/...` и `/garage/...` нет. |
| id | Числа (BIGINT), не UUID. Для ссылок «Поделиться» у магазина и запчасти есть отдельный непредсказуемый `publicId` (10 символов base62) — см. 6 и 4.4. |
| Вход | `Authorization: Bearer {accessToken}` (SMS-код → `POST /auth/otp/verify`). Каталог, карта, марки, категории, профиль магазина открыты гостю. |
| Язык | `Accept-Language: ru` или `ky`; в данных язык — `RU` / `KG`. |
| Ошибки | `{code, message, fields?}`; `code` — стабильный (`REQUEST_EXPIRED`, `EXTEND_LIMIT`…), `message` — для показа. 429 — ещё `retryAfter`. |
| Идемпотентность | `Idempotency-Key` на `POST /requests`, `POST /requests/{id}/replies`, сообщения чата (`clientId`). Без сети клиент повторяет с тем же ключом. |
| Цена | Целые сомы, `currency: "KGS"`. Фронт форматирует «3 200 сом». |
| Время | ISO-8601 UTC. «12 мин назад», «9:38», «вчера» — на клиенте. Рабочие часы магазинов — по Бишкеку. |
| Место | Всегда отдельно код ряда (`row` / `rowCode`), номер контейнера (`container` / `number`) и `side`. |
| Машина | `brand {id, slug, name, shortName, logoUrl, placeholder, color, popular}` + подпись модели + год. |
| Счётчики | Все бейджи и чипы считает сервер. |
| Фото | Загрузка через бэкенд: `POST /media/photos` (multipart, `file`, `purpose`) → `{id, url, thumbUrl, width, height}`; дальше `id` передаётся в `mediaIds` сущности. Сервер пережимает в 1080 и 320 px и убирает EXIF. **Приложение само сжимает фото до 1080 px по длинной стороне (JPEG) перед отправкой** — на рынке слабый мобильный интернет; сервер принимает до 10 МБ. HEIC не принимается. |
| Живые события | **Centrifugo**, не STOMP. См. раздел 11. |

---

### 1. Енамы

```java
enum UserRole        { BUYER, SELLER }                              // [03]
enum Lang            { RU, KG }                                     // [01, 19, 21]
enum Theme           { LIGHT, DARK, SYSTEM }                        // [19]

enum RowType         { ROW, VROW, STALL }                           // ряд / вертикальный ряд / жайма
enum Side            { NORTH, SOUTH, EAST, WEST }                   // «северная сторона» [10, 18, 22, 30]

enum ShopStatus      { PENDING_VERIFICATION, ACTIVE, BLOCKED }      // на проверке / работает / заблокирован

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

enum MediaPurpose    { PART, SHOP, AVATAR, REQUEST, REPLY }         // для POST /media/photos
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

---

### 2. Справочники

#### 2.1 Марки — `GET /brands?popular=&q=`, `GET /brands/{id}/models`, `GET /models?brandId=&q=`
`BrandDto {id, slug, name, shortName, logoUrl?, placeholder, color, popular}`. `shortName` — подпись в плитке [23, 28] («Mercedes»). `logoUrl = null` — круг с буквой `placeholder` цветом `color`. Поиск `q` — по названию, короткому имени и народным названиям («мерс», «бэха», «камри»).

18 марок. Популярные (8 плиток, в этом порядке): Toyota, Lexus, Mercedes-Benz, BMW, Honda, Nissan, Hyundai, Kia. Остальные — «Все марки А–Я».

Модель — модель + поколение: подпись `label` «Camry 50», «E-класс W211»; годы — для проверки года машины и фитментов.

#### 2.2 Категории — `GET /categories`
`CategoryDto {id, slug, name}` на языке запроса, в порядке чипов: Ходовая · Тормоза · Двигатель · Оптика · Кузов · Электрика · Охлаждение · Трансмиссия · Салон · Фильтры и ТО · Другое.

#### 2.3 Подсказки «что нужно» [06] — `GET /requests/hints?carId=&limit=3`
`[{id, text, categoryId}]` — «Колодки», «Стойки», «Фара»… Сначала то, что чаще спрашивали за 90 дней для этой модели, потом марки, дальше самые частые вообще. Выбранная подсказка передаётся в `hintId` запроса — категория запроса берётся из неё.

---

### 3. Пользователь и гараж

- `GET /me` → `{id, phone, name?, avatarUrl?, role, lang, hasShop, …}`; `PATCH /me {name?, lang?, avatarMediaId?}`; `DELETE /me/avatar`; `PUT /me/role {role}` [03].
- `GET/PATCH /me/settings` → `{notifyReplies, notifyChat, newRequestSound, theme}` [19, 21].
- `POST /devices {token, platform}` / `DELETE /devices/{token}` — FCM.
- Гараж [04]: `GET /me/cars`, `POST /me/cars {modelId, year, engine?, vin?, …}`, `PATCH /me/cars/{id}`, `POST /me/cars/{id}/primary`, `DELETE /me/cars/{id}`. Машина: `brand`, модель, `year`, `isPrimary`; подпись «Camry 50 · 2012».
- Избранное: `GET /me/favorite-parts`, `GET /me/favorite-shops`.

---

### 4. Поиск запчастей [27, 28, 29, 30]

#### 4.1 `GET /parts/search`

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

#### 4.2 Ответ
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

#### 4.3 Карточка [29] — `GET /parts/{id}?carId=|brandId=&modelId=&year=`
`PartDetailDto {id, publicId, status, title, price, condition, quantity, stockStatus, category, manufacturer?, oemNumber?, side?, position?, photos[], fitments[], fit? {carLabel, fits}, isFavorite, shop: ShopCardDto, shopPhotos[3], publishedAt, updatedAt}`. `fit` — «Подходит к вашей Camry 50 · 2012». Открытие — просмотр (не чаще раза в час на человека).
Избранное: `PUT` / `DELETE /parts/{id}/favorite`.

#### 4.4 Ссылка «Поделиться»
- Запчасть: `https://kudaibergen.kg/p/{publicId}` → `GET /parts/public/{publicId}` (ответ как у `/parts/{id}`).
- Магазин: `https://kudaibergen.kg/s/{publicId}` → `GET /shops/public/{publicId}` (ответ как у `/shops/{id}`).
- В ссылках — только `publicId`, не числовой `id`.

---

### 5. Каталог продавца [24, 25, 26]

- `GET /my/parts?filter=ALL|IN_STOCK|OUT_OF_STOCK|DRAFT|ARCHIVED&q=&cursor=` → строки `{id, status, title, mainPhoto?, brands[], fitmentLabels[], price?, quantity, stockStatus, views}`.
- `GET /my/parts/summary` → чипы `{all, inStock, outOfStock, drafts, archived, viewsWeek, activeLimit}`: «Все · 38 / В наличии · 32 / Нет · 6», «посмотрели **214 раз** за неделю».
- `GET /my/parts/{id}` — форма редактирования.
- `POST /parts?publish=false` (черновик) → `PATCH /parts/{id}` → `POST /parts/{id}/publish`; `POST /parts/{id}/archive` / `restore`; `DELETE /parts/{id}` (владелец).
- Тело: `{title, categoryId, condition, price, quantity, manufacturer?, oemNumber?, side?, position?, mediaIds[] (1–6, первое — главное), fitments[{brandId, modelId?, yearFrom?, yearTo?}] (1–20)}`. Порядок фото = порядок `mediaIds`.
- Публикация требует: название 3–120, категорию, состояние, цену, 1–6 фото, 1–20 машин; иначе 400 `PART_INCOMPLETE` с `missing[]`. Не больше 500 опубликованных.
- Камера «Номер детали» [25]: `POST /ocr/oem` (multipart) → кандидаты номера.
- Импорт: `GET /my/parts/import/template`, `POST /my/parts/import` (.xlsx) → черновики и отчёт по строкам.

---

### 6. Магазин [10, 10а, 21, 22, 23, 30]

- Сетка выбора бокса [10а]: `GET /market/rows`, `GET /market/rows/{id}` — контейнеры по сторонам, `occupied` — серые.
- Регистрация: `POST /shops {containerId, name, brandIds[], categoryIds[], openFrom?, openTo?, workDays?}` → `PENDING_VERIFICATION`. Проверка: `GET /my/shop/verification`, `POST /my/shop/verification/qr {qrToken, lat?, lon?}`, по SMS арендатора (`…/sms/send`, `…/sms/confirm`) или через админа (`…/admin-request`).
- `GET /my/shop`; `PATCH /my/shop {name?, openFrom?, openTo?, workDays?, phone?, phoneVisible?}`; `PUT /my/shop/brands {brandIds}` [23] («Сохранить · 4 марки»); `PUT /my/shop/categories {categoryIds}`.
- Тумблер «Бокс закрыт» [11, 21]: `PATCH /my/shop/open {isOpen}` — закрытым запросы не рассылаются.
- Аватар: `PUT /my/shop/avatar {mediaId}`, `DELETE /my/shop/avatar`. Фото места (до 8, первое — обложка): `GET/POST /my/shop/photos`, `PUT /my/shop/photos/order`, `POST /my/shop/photos/{mediaId}/cover`, `DELETE /my/shop/photos/{mediaId}`.
- Сотрудники [21]: `GET/POST /my/shop/members`, `DELETE /my/shop/members/{userId}`. Переезд: `POST/DELETE /my/shop/relocation`.
- Отзывы: `GET /my/shop/reviews`, `POST /my/shop/reviews/{id}/reply` (один раз). Шаблоны ответов: `/my/shop/reply-templates`.
- Публичный профиль [30]: `GET /shops/{id}` → `{id, publicId, name, avatarUrl?, rating, reviewsCount, location {rowId, rowCode, rowLabel, containerId, number, side}, open {openNow, closedManually, openFrom, openTo, workDays, opensAt?}, brands[], categories[], phone?, isFavorite, photos[], counts {parts, photos, reviews}}`. Ещё `GET /shops/{id}/photos`, `GET /shops/{id}/reviews`, `PUT/DELETE /shops/{id}/favorite`, список «Списком» — `GET /shops?brandId=&categoryId=&rowId=&q=`.

---

### 7. Запросы [05–07, 06б, 09, 11, 12, 20, 31, 32]

#### 7.1 Отправка [06, 06б, 31]
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

#### 7.2 Покупатель
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

#### 7.3 Продавец [11, 12, 14]
- `GET /my/shop/requests?filter=NEW|ANSWERED|EXPIRED|UNANSWERED&cursor=` → `{id, text, car, category?, photos[], buyerName?, status, soldHere, createdAt, notifiedAt, expiresAt, seen, myReply?}`. `NEW` — активные без ответа (таймер «осталось 12 мин» по `expiresAt`); `EXPIRED` — «Истёкшие»; `UNANSWERED` — пропущенные (время вышло или закрыт, ответа не было).
- `GET /my/shop/requests/{id}`; `POST /my/shop/requests/{id}/seen` — продавец открыл карточку или нажал на пуш.
- `GET /my/shop/requests/{id}/suggested-parts` — свои запчасти под машину для «Приложить товар».
- `POST /requests/{id}/replies {answer, condition?, message?, price?, partId?, mediaIds[]≤3}` (purpose `REPLY`). «Есть» требует `condition`. После `expiresAt` — 409 `REQUEST_EXPIRED`. Изменить — `PATCH /requests/{id}/replies/mine` в течение 10 минут.

---

### 8. Чат [08, 13, 16]

- `POST /chats {shopId, requestId?, partId?}` — открыть (по запросу, прямой из профиля, с карточкой товара). Ответ «Есть» открывает чат сам (`chatId` в ответе).
- `GET /chats?cursor=` [16] — строки `{id, mySide, title, avatarUrl?, subtitle, requestId?, requestClosed, lastMessage?, unread, online, blocked, updatedAt}`; `GET /chats/unread` — бейдж.
- `GET /chats/{id}`, `GET /chats/{id}/messages?cursor=`, `POST /chats/{id}/messages {text?|quick?, clientId}`, `POST /chats/{id}/messages/media` (фото, голосовое до 60 с, видео), `POST /chats/{id}/read`.
- `GET /chats/{id}/quick-replies` — быстрые ответы для своей стороны.
- `PUT/DELETE /chats/{id}/block`, `POST /chats/{id}/complaints`.
- Живая доставка — Centrifugo, раздел 11.

---

### 9. Карта и маршрут [15, 18]

- `GET /market/map` (ETag `"map-v{version}"`, с `If-None-Match` — 304) → граница, ряды, проходы, входы, POI, матрица GPS.
- `GET /market/search?q=` — «14», «ряд ю», «14 12». `GET /market/qr/{token}` — QR ряда или контейнера → точка схемы. `POST /market/locate {lat, lon}` → точка.
- Подсветка [15]: `GET /market/map/highlight?kind=BRAND|CATEGORY&id=&fromX=&fromY=|fromLat=&fromLon=|fromEntrance=` →
  `{kind, id, name, rowIds[], containerIds[], shopsCount, rowsCount, openNowCount, nearestRowId?, nearestRow?, nearestContainerId?, nearestContainer?, nearestShopId?, nearestDistanceM?, nearestMinutes?, fromSource}` — «Mercedes-Benz — в 6 рядах · 21 бокс · ближайший ряд 14, 170 м».
- Маршрут [18]: `GET /market/route?toContainerId=&fromX=&fromY=|fromLat=&fromLon=|fromEntrance=` → `{target, containerPoint, from, fromSource, polyline[[x,y]], distanceM, minutes, steps[]}`. Без точки и GPS — от главного входа.

---

### 10. Статистика бокса [17]

`GET /my/shop/stats?period=WEEK|MONTH` (последние 7 / 30 дней):
```json
{ "period": "WEEK", "from": "…", "to": "…",
  "requestsByBrands": 128, "answeredHave": 46, "answeredNotHave": 40, "wroteInChat": 31, "buyersArrived": 12,
  "sales": 19, "unanswered": 22, "avgReplyMinutes": 4, "partViews": 214,
  "topCategories": [{ "categoryId": 1, "name": "Ходовая", "count": 24 }] }
```
`unanswered` — пропущенные (время вышло или закрыт, ответа не было); «Смотреть» → `GET /my/shop/requests?filter=UNANSWERED`. `sales` — запросы, закрытые «Купил» у бокса. `topCategories` — до 4, по запросам с выбранной категорией.

---

### 11. Живые события — Centrifugo

Не STOMP. Клиент — официальный SDK Centrifugo (`centrifuge-js` / `centrifuge` для React Native), протокол WebSocket.

1. `GET /realtime/token` → `{token, expiresInSeconds}` — токен подключения; SDK обновляет его через `getToken`-коллбэк тем же запросом.
2. Сразу после подключения — подписка на **личный канал** `inbox:{userId}#{userId}` (без отдельного токена; Centrifugo пускает туда только этого пользователя). Presence на нём = «в сети».
3. Открыли чат — подписка на `chat:{chatId}` с токеном `GET /chats/{id}/subscription-token`; закрыли — отписка.

Все события — конверт `{type, payload}`:

| канал | type | payload |
| --- | --- | --- |
| `inbox:{userId}#{userId}` | `CHAT` | обновлённая строка списка чатов [16] |
| `inbox:{userId}#{userId}` | `UNREAD` | `{asBuyer, asShop}` — бейджи непрочитанных |
| `inbox:{userId}#{userId}` | `REQUEST_STATS` | статистика своего запроса — как `GET /requests/{id}/stats` [32] |
| `chat:{chatId}` | `MESSAGE` | сообщение |
| `chat:{chatId}` | `READ` | `{chatId, side, readMessageId}` |
| `chat:{chatId}` | `TYPING` | публикует сам клиент |

Источник истины — REST: после переподключения клиент перечитывает открытый экран (история каналов 10 минут догоняет короткие обрывы).
