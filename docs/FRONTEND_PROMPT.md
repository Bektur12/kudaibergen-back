Стек: Expo 57 (expo-router), React Native 0.86, TypeScript, NativeWind, TanStack Query, Zustand. Все 31 экран уже свёрстаны по макету на демо-данных из `src/mocks/index.ts`.

ЗАДАЧА: подключить приложение «Кудайберген» к настоящему бэкенду — заменить моки на API, сделать вход, живые события и загрузку фото. **Вёрстку не менять**: после подключения каждый экран должен выглядеть так же, как `design/screens/<экран>.png`.

СНАЧАЛА ПРОЧИТАЙ ЦЕЛИКОМ:
1. `design/HANDOFF.md`, особенно раздел «Бэкенд».
2. Контракт API: `~/IdeaProjects/kudaibergen-back/docs/BACKEND_SPEC.md` (репозиторий бэкенда, ветка `v2`). Он главный: пути, поля, енамы, коды ошибок, каналы Centrifugo, типы пушей.
3. Swagger запущенного бэкенда: `http://localhost:8080/swagger-ui.html`, схема — `http://localhost:8080/v3/api-docs`.
Если спецификация и Swagger расходятся, верь Swagger и запиши расхождение в `BACKEND_QUESTIONS.md`.

БЭКЕНД ЛОКАЛЬНО:
- `cd ~/IdeaProjects/kudaibergen-back && docker compose up -d` — Postgres, Redis, Centrifugo (порт 8000), MinIO.
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
4. Экраны без макета — **06б** (кому отправить: весь рынок / ряды / контейнеры + «Сколько ждать ответы»), **31** (выбор контейнеров ряда: `GET /market/rows/{id}/containers?brandId=`, «продают Toyota: 9», предупреждение, если марки нет), **32** (статистика запроса, таймер, «Продлить», «Отправить всему рынку»). Собрать из существующих компонентов и токенов в стиле 06 и 07; содержание — `BACKEND_SPEC.md` 7.1–7.2. Сделать и показать мне скриншоты до перехода дальше.
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
