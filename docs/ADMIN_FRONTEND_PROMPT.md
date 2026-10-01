# Промпт: интеграция веб-админки «Кудайберген» с бэкендом

Скопируй всё ниже целиком в агента, который работает в репозитории админки (`~/Projects/kudaibergen-admin`).

---

## Задача

Ты работаешь в репозитории веб-админки авторынка «Кудайберген» (React 19, TypeScript, Vite, TanStack Query,
openapi-fetch, MSW). Бэкенд админки готов полностью: все экраны A1–A9 плюс «Пользователи», «Журнал»
и «Сотрудники». Сейчас фазы 1–2 (вход, продавцы, мастера, арендаторы, споры) ходят в настоящий API, а фазы 3–7
работают на MSW по черновику `openapi/admin.draft.yaml`. Пути, поля и форматы черновика расходятся с реальным API.

**Цель:** перевести все экраны на настоящий бэкенд. Черновик, его клиент и его моки удалить. Живые обновления
перевести со STOMP на Centrifugo. После этого админка работает с `VITE_USE_MOCKS=false` без единого мока.

Бэкенд не меняй. Если поля не хватает или контракт неудобен, запиши это в список «вопросы к бэкенду» в конце
отчёта. Не обходи это вычислениями на клиенте.

## Источники правды

1. **OpenAPI бэкенда** — `/v3/api-docs` (Swagger UI `/swagger-ui.html`), теги «Админка: …». Это источник точных
   схем. Сначала сними снапшот: `npm run api:pull`, результат попадёт в `openapi/backend.json` и
   `src/shared/api/backend.d.ts`. Все типы бери оттуда, руками DTO не описывай.
2. **Правила, которых нет в схеме**, — `docs/ADMIN_API.md` в репозитории бэкенда
   (`~/IdeaProjects/kudaibergen-back/docs/ADMIN_API.md`). Прочитай его целиком перед началом.
3. **Макет** — `docs/admin-design.html`. API под него сверен.

## Запуск бэкенда локально

```bash
cd ~/IdeaProjects/kudaibergen-back
docker compose up -d                  # Postgres, Redis, Centrifugo (порт 8000)
mvn spring-boot:run                   # API на :8080
python3 scripts/demo_seed.py          # демо-данные (только локальная база!)
```

Демо-сотрудники, пароль у обоих `Kudaibergen2026`:

- `+996555000010` — `SUPER_ADMIN`, есть все права;
- `+996555000011` — `MARKET_ADMIN`, права урезаны: на нём проверяй, что лишнее спрятано.

SMS-код при входе в dev приходит в ответе `login` полем `debugCode`.

`.env.local` админки:

```
VITE_API_URL=
VITE_PROXY_TARGET=http://localhost:8080
VITE_CENTRIFUGO_URL=ws://localhost:8000/connection/websocket
VITE_USE_MOCKS=false
```

`VITE_WS_URL` (STOMP) удали, вместо него заведи `VITE_CENTRIFUGO_URL`. Обнови `.env.example` и `env.ts`.

## Общие правила API (уже частично реализованы в `src/shared/api`)

- Все пути админки — `/api/v1/admin/**`. Даты приходят в ISO 8601 UTC. «2 ч назад» и «вчера» считает фронт,
  день по Бишкеку (UTC+6).
- **Сессия.**
  - Access-токен хранится в памяти и уходит в `Authorization: Bearer`. Refresh лежит в httpOnly-cookie
    `admin_refresh`, поэтому все запросы шлются с `credentials: 'include'`.
  - При загрузке страницы сразу вызывай `POST /admin/auth/refresh`. На 401 один раз делай refresh и повторяй
    запрос, снова 401 — экран входа.
  - Это уже сделано в `authFetch`/`session.ts`, не ломай.
- **Лимит входа** считает только неверные пароли. `OTP_COOLDOWN` на `login` значит «пароль верный, код уже
  отправлен»: переходи к вводу кода с отсчётом `retryAfter`.
- **Вход:**
  - `POST /admin/auth/login {phone, password}`, затем `POST /admin/auth/verify {phone, code}`;
  - «Задать пароль»: `POST /admin/auth/password/code {phone}`, затем `POST /admin/auth/password {phone, code, password}`;
  - `POST /admin/auth/logout`.
- **Ошибки** — RFC 7807 `{type, title, status, detail, code, errors?[{field, message}], retryAfter?}`.
  - Ветвись по `code`, а `detail` показывай как есть: он уже на русском и человеческий.
  - 400 `VALIDATION_ERROR` — подсвети поля из `errors[]`.
  - 403 — «Нет прав».
  - 409 — конфликт, покажи `detail` в тосте.
  - 429 — используй `retryAfter`.
- **Права.** `GET /admin/me` → `{userId, phone, fullName, title, role, permissions[], badges, lastLoginAt}`.
  - Пункты меню, табы и кнопки показывай только при наличии права из `me.permissions`. Роль в коде не сравнивай.
  - Сервер проверяет права сам, фронт только прячет.
- **Бейджи сайдбара** — `me.badges`: `shopsPending`, `mastersPending`, `complaintsNew`, `disputesOpen`.
  `null` означает «нет права на раздел»: бейдж не показывай. Перезапрашивай `me` после действий, которые меняют
  счётчики, и раз в минуту.
- **Телефоны** приходят полными или маской `+996 *** ** 34 56`, решает сервер по праву `PII_VIEW`. Показывай
  как есть и ничего не форматируй поверх маски.
- **Списки** приходят в виде `{items, nextCursor, counts?}`:
  - курсор непрозрачный, `nextCursor = null` значит конец (используй `useInfiniteQuery`);
  - `counts` — серверные счётчики табов и чипов. Цифры в табах бери только оттуда и не считай по `items`.
- **Enum'ы** приходят кодами, подписи RU/KG делает фронт (словарь подписей в одном месте).
- **Картинки и файлы** приходят абсолютными URL. Относительные (`/api/v1/brands/{id}/logo`, `/assets/...`)
  прогоняй через `assetUrl()`.
- **Excel**: `GET …/export.xlsx` с теми же фильтрами, что у списка. Скачивай через `downloadFile()` (fetch с
  Bearer → blob), имя файла бери из `Content-Disposition`. Кнопку показывай при `EXPORT_EXCEL` и праве на раздел.
- **Аудит**: каждое изменяющее действие сервер пишет в журнал сам, фронту ничего делать не нужно.

## Что сделать по шагам

1. `npm run api:pull` при запущенном бэкенде. Убедись, что `backend.d.ts` содержит все пути ниже.
2. Переведи каждую фичу с `client` (черновик) на `api` (реальный). Пути бери из таблицы ниже, поля — из
   `backend.d.ts`. Где форма данных черновика отличается, перепиши компонент под реальный DTO, а не наоборот.
3. Удали `client`, `openapi/admin.draft.yaml`, `src/shared/api/schema.d.ts`, черновые хэндлеры в
   `src/mocks/handlers.ts` и режим `missing` в `env.ts` и `mocks/browser.ts`. Режим `all` оставь, только если на
   нём держатся тесты. Тогда переведи моки на реальные пути и типы из `backend.d.ts`.
4. Замени `realtime.ts` на Centrifugo (раздел ниже).
5. `npm run typecheck && npm run lint && npm test && npm run build` должны пройти без ошибок.
6. Пройди руками каждый экран под обоими демо-сотрудниками (чек-лист в конце).

## Соответствие «черновик → реальный API»

Все реальные пути даны от `/api/v1`. «=» означает, что путь совпадает и сверить нужно только поля.

### Сводка [A1] — `DASHBOARD_VIEW`

Один `GET /admin/dashboard` из черновика разбит на независимые запросы. Каждый виджет грузится сам, у каждого
свой `generatedAt` → подпись «данные обновлены N мин назад».

| черновик | реальный |
| --- | --- |
| `GET /admin/dashboard` (KPI) | `GET /admin/dashboard/kpi` → `requestsToday`, `requestsDeltaPct`, `haveIn30Pct`, `haveIn30DeltaPp`, `activeShops`, `activeShopsNewWeek`, `serviceRequestsToday`, `serviceDeltaPct`. `null` в дельте — не с чем сравнить, показывай «—» |
| график по дням | `GET /admin/dashboard/requests-by-day?range=D14\|D30\|QUARTER` → `{date, requests, withHave, serviceRequests}[]` |
| «Без ответа по маркам» | `GET /admin/dashboard/unanswered-by-brand?period=WEEK\|MONTH`. `hint.text` выводи как есть. Кнопка «Сделать рассылку» открывает создание рассылки с `audience=SELLERS` и `brandIds=hint.broadcastBrandIds` |
| «Чаще всего ищут» | `GET /admin/dashboard/top-searched?period=WEEK\|MONTH` |
| «Ждут действий» | `GET /admin/dashboard/pending` → `shops`, `masters`, `complaints`, `disputes`, `latest[{kind, id, title, phone, createdAt}]`; `title` — готовый текст, словарём не переводить |
| — | `POST /admin/dashboard/refresh` — кнопка «Обновить», затем инвалидируй все запросы сводки |
| `GET /admin/dashboard/export.xlsx` | = |

### Рынок и карта [A3] — `MARKET_VIEW` / `MARKET_EDIT` / `MARKET_MAP_PUBLISH`

| черновик | реальный |
| --- | --- |
| `GET /admin/market/map` | = , но ответ — `{current, published, draft?}`. `current` — шапка «версия N · опубликована …». Редактор стартует с `draft.data`, а если черновика нет — с `published`. У `draft` есть `outdated`, `valid`, `error` — покажи их |
| `PUT /admin/market/map/draft` | = (схема с ошибкой тоже сохраняется) |
| — | `DELETE /admin/market/map/draft` — «Сбросить черновик» |
| `POST /admin/market/map/publish` | = , тело необязательно: `{comment}` — поле «Что изменилось» для журнала; 409 `NO_DRAFT`, 400 `BAD_MAP`. У улиц `name`/`labelX`/`labelY` бывают `null` — рисуй полигон без подписи |
| список рядов | `GET /admin/market/rows?includeInactive=` → счётчики `containers`, `withShop`, `free`, `disabled`, `bySide`; `counts` по рынку |
| `GET /admin/market/rows/{id}` | = ; сетка мест по сторонам: номер, позиция, арендатор, `shop`, `incoming`, QR |
| `POST /admin/market/rows/{id}/containers` | **два разных действия:** `PUT /admin/market/rows/{id}/containers {counts}` — число мест по сторонам; `POST /admin/market/containers {rowId, side, number, posInRow?, tenantName?, tenantPhone?}` — одно новое место (409 `CONTAINER_EXISTS`, 400 `WRONG_SIDE`) |
| `PATCH /admin/market/containers/{id}` | = ; пустая строка в `tenantName` или `tenantPhone` убирает значение |
| — | `DELETE /admin/market/containers/{id}` — 409 `CONTAINER_OCCUPIED` / `CONTAINER_IN_USE` (тогда предложи «Выключить») |
| `GET/PUT /admin/market/anchors` | `GET/PUT /admin/market/geo-anchors {anchors}` |
| QR | `GET /admin/market/qr/{token}.png`, `GET /admin/market/qr/sheet?rowId=` — тоже через `authFetch` → blob |

### Модерация [A4] — `COMPLAINTS_VIEW` / `COMPLAINTS_RESOLVE`

| черновик | реальный |
| --- | --- |
| `GET /admin/complaints` | = , фильтры `status=OPEN\|RESOLVED\|REJECTED&type=&cursor=`; `counts: {open, resolved, rejected}` |
| `GET /admin/complaints/{id}` | = ; `subject`, `party`, `partyStats` (`complaints90d`, `warnings90d`, `removed90d`), заявитель, связанный запрос, `sameTargetOpen` |
| `POST /admin/complaints/{id}/resolve` | = , тело `{action: REMOVE_CONTENT\|WARN_SELLER\|BLOCK_SHOP\|UNFOUNDED, comment}` → `{complaint, resolvedTogether}`. Если `resolvedTogether > 0`, покажи «Закрыто ещё N жалоб на этот объект» |

- **Новое по макету:** у `party` есть `rating` и `reviewsCount`. Для магазина и мастера выводи «★ 4.6 · 38 отзывов»,
  для пользователя они `null`, и строку не показывай.
- Кнопки действий показывай по правам:
  - `REMOVE_CONTENT` — `CONTENT_REMOVE`;
  - `WARN_SELLER` — `SELLER_WARN`;
  - `BLOCK_SHOP` — `SELLERS_BLOCK`, `MASTERS_BLOCK` или `USERS_BLOCK` в зависимости от `party.type`.
- 400 `NOTHING_TO_REMOVE` — магазин, мастер или чат целиком не скрываются. Для таких жалоб кнопку «Удалить»
  не показывай.

### Справочники [A5, A9] — `DICTIONARIES_VIEW` / `DICTIONARIES_EDIT`

Всё переезжает под `/admin/dictionaries/…`. Списки отдаются целиком (`nextCursor = null`), `counts` — по табам
«все / действуют / скрыты». Скрытие делается через `active = false`. Удаление при использовании даёт 409 `IN_USE`,
а в `detail` написано, где запись используется: покажи его и предложи «Скрыть».

| черновик | реальный |
| --- | --- |
| `GET /admin/brands` | `GET /admin/dictionaries/brands?q=` (`modelsCount`, `sellersCount`, `mastersCount`, `carsCount`) |
| `GET/POST/PATCH/DELETE /admin/brands/{id}` | `/admin/dictionaries/brands`, `/admin/dictionaries/brands/{id}` |
| — | `PUT /admin/dictionaries/brands/order {ids}` — drag-and-drop |
| `POST /admin/brands/{id}/logo/presign` + `PUT …/logo` | **presign нет.** `POST /admin/media/photos` (multipart `file`) → `{id, …}`, затем `PUT /admin/dictionaries/brands/{id}/logo {mediaId}`; `DELETE …/logo` убирает лого. Прозрачный фон станет белым — предупреди в подсказке |
| `GET/POST /admin/brands/{id}/models` | `GET/POST /admin/dictionaries/brands/{brandId}/models` |
| `PATCH/DELETE /admin/models/{id}` | `/admin/dictionaries/models/{id}`; `generation: ""` и `displayName: ""` стирают значение, `null` — не менять |
| `GET /admin/service-types`, `PUT …/order`, `PATCH/DELETE …/{code}` | `/admin/dictionaries/service-types…`; порядок — `PUT …/order {codes}` (все коды). Поля панели: `nameRu`, `nameKg`, `icon`, `needsLocation`, `urgent`, `defaultDuration` (`MIN_15\|MIN_30\|HOUR_1\|HOUR_3`), `defaultRadiusKm`, `active`; в списке ещё `mastersCount`, `requests30d` |
| `GET/POST/PATCH/DELETE /admin/dictionaries/{kind}[/{id}]` | отдельные пути по видам: `/admin/dictionaries/categories` (+ `PUT order {ids}`), `/admin/dictionaries/synonyms` (POST `{term, synonym, bidirectional}` возвращает **список** созданных пар; изменения нет — удали и создай заново), `/admin/dictionaries/hints` (`categoryId = 0` убирает категорию) |

`aliases` у марки и модели — массив. `PATCH` с `aliases` заменяет список целиком.

### Рассылки [A6] — `BROADCASTS_VIEW` / `BROADCASTS_SEND`

| черновик | реальный |
| --- | --- |
| `POST /admin/broadcasts/estimate` | **`GET`** `/admin/broadcasts/estimate?audience=ALL\|BUYERS\|SELLERS\|MASTERS&brandIds=&rowIds=&serviceTypes=` → `{recipients, withDevices, label}`. `label` («Получат 64 продавца») выводи как есть. Запрос шли с debounce при изменении фильтров. 400 `BAD_FILTER` — фильтр не подходит аудитории (`rowIds` — только продавцы, `serviceTypes` — только мастера) |
| `POST /admin/broadcasts` | = , `{audience, filters, titleRu, titleKg?, bodyRu, bodyKg?}` → черновик |
| — | `PATCH /admin/broadcasts/{id}` — правка черновика (409 `BROADCAST_NOT_DRAFT`) |
| отправка | `POST /admin/broadcasts/{id}/schedule {at}` или `{now: true}`. Если в ответе `deferred = true`, покажи «Тихие часы 22:00–07:00 — уйдёт в 07:00» |
| — | `POST /admin/broadcasts/{id}/cancel` (409 `BROADCAST_FINISHED`) |
| `GET /admin/broadcasts` | = , `?status=&cursor=`; `recipients`, `delivered`, `opened`, `openedPct`; `counts: {all, drafts, scheduled, sent}`. `GET /admin/broadcasts/{id}` |

Отправка идёт в фоне: раз в 20 с батчами. Пока у рассылки идёт отправка, перезапрашивай её каждые 10–15 с.

### Продавцы, мастера, арендаторы, споры [A2, A7]

Это уже на реальном API. Проверь только:

- после `api:pull` нет ошибок типов;
- экспорт: `GET /admin/shops/export.xlsx?tab=&q=` и `GET /admin/masters/export.xlsx?tab=&q=&service=&brandId=`;
- у мастера колонка «Проверка» (`check`) — `COMPLAINT` / `NO_PHOTOS` / `PHOTOS`. «Документы» пока не делаем:
  загрузки документов на бэке нет.

### Запросы и заявки [A8] — `REQUESTS_VIEW` / `REQUESTS_MANAGE`

| черновик | реальный |
| --- | --- |
| `GET /admin/part-requests` | = , `?period=TODAY\|WEEK\|MONTH\|ALL&status=&noReplies=&q=&cursor=`; `counts: {parts, services, partsNoReplies, servicesNoReplies}` → табы «Запросы на запчасти · 342», «Заявки на услуги · 57», «Без откликов · 6» |
| `GET /admin/service-requests` | = , плюс `&service=` |
| `GET /admin/part-requests/{id}`, `/admin/service-requests/{id}` | = |
| `POST …/widen` | = для обоих видов: запчасти расширяются до всего рынка, услуги — радиус +5 км |
| `POST …/hide` | = , тело `{reason}` обязательно |
| `…/export` | `GET /admin/part-requests/export.xlsx`, `GET /admin/service-requests/export.xlsx` с фильтрами списка |

- Статусы: `ACTIVE`, `AGREED`, `NO_REPLIES`, `EXPIRED`, `CLOSED`, `HIDDEN`.
- Счётчики строки: `received / seen / positive` → «получили / посмотрели / могут».
- **Новое по макету:** в откликах на заявку у каждого отклика есть `masterMobile: boolean`. Если `true`, рядом
  с именем мастера ставь пометку «выездной».

### Пользователи — `USERS_VIEW` / `USERS_BLOCK` / `MESSAGE_USERS`

| черновик | реальный |
| --- | --- |
| `GET /admin/users` | = , `?role=ALL\|BUYER\|SELLER\|MASTER\|BLOCKED&q=&cursor=`; `counts: {all, buyers, sellers, masters, blocked}` |
| `GET /admin/users/{id}` | = ; гараж, бокс с ролью, профиль мастера, последние запросы и заявки, жалобы, санкции |
| `POST …/block` | = , `{reason}`; 409 `ALREADY_BLOCKED`, `SELF_BLOCK`, `STAFF_BLOCK` (сотрудник админки — отключают в «Сотрудниках») |
| `POST …/unblock` | = |
| — | `POST /admin/users/{id}/message {text}` → `{recipients}` — кнопка «Написать» |

### Журнал — `AUDIT_VIEW`

| черновик | реальный |
| --- | --- |
| `GET /admin/audit` | = , `?adminId=&action=&entityType=&entityId=&from=&to=&cursor=`; `counts: {today, week}` |
| — | `GET /admin/audit/facets` → `{actions, entityTypes}` для выпадающих фильтров |
| `GET /admin/audit/{id}` | = ; `before` / `after` — JSON, покажи diff (у действий без объекта они `null`) |

### Сотрудники — `STAFF_MANAGE`

| черновик | реальный |
| --- | --- |
| `GET/POST /admin/staff` | = ; POST `{phone, fullName, title?, role}`, 409 `STAFF_EXISTS`. В списке есть `hasPassword`: если `false`, пиши «Пароль не задан — сотрудник задаёт его сам на экране входа» |
| `PATCH /admin/staff/{id}` | `PATCH /admin/staff/{userId} {role?, title?, fullName?, isActive?}`; 409 `STAFF_SELF`, `LAST_SUPER_ADMIN` |
| `GET /admin/roles` | `GET /admin/staff/roles` → `{roles: [{role, defaultTitle, editable, permissions}], allPermissions}` |
| — | `PUT /admin/staff/roles/{role}/permissions {permissions}` — матрица прав. `editable = false` (SUPER_ADMIN) — только чтение, иначе 400 `ROLE_FIXED`. После сохранения перезапроси `me` |

### Шапка

`GET /admin/search?q=` → `{users, shops, masters, containers}`, в каждой группе до 5 результатов. Группа `null`
означает «нет права», и её не показывай. `[]` означает «ничего не нашлось». Запрос шли от 2 символов, с debounce.

## Живые обновления: STOMP → Centrifugo

В бэкенде нет STOMP, есть Centrifugo v6. Перепиши `src/shared/api/realtime.ts` на `centrifuge` (npm `centrifuge`).
Убери `@stomp/stompjs` из зависимостей.

- Токены: `GET /api/v1/admin/realtime/token` → `{connectionToken, subscriptionToken, channel: "admin:requests"}`.
  Нужно право `REQUESTS_VIEW`. Без права realtime не подключай.
- Подключение:

  ```ts
  new Centrifuge(env.centrifugoUrl, { getToken: fetchConnectionToken })
  ```

  Подписка:

  ```ts
  client.newSubscription(channel, { getToken: fetchSubscriptionToken })
  ```

  Обе функции берут токены через `api.GET('/api/v1/admin/realtime/token')`. Centrifuge сам вызывает их при
  истечении токена. Если вызов вернул 401/403, верни пустую строку, и клиент перестанет переподключаться.
- Событие: `{type: "REQUEST_CHANGED", payload: {kind: "PART" | "SERVICE", id, event}}`, где `event` — `DISPATCHED`,
  `REPLY`, `EXPIRED`, `CLOSED` или `HIDDEN`. На событие инвалидируй список нужного вида и, если она открыта,
  карточку `id`. Лучше делать это пачкой с throttle ~1 с.
- Отдельного канала счётчиков (`/topic/admin/counters`) нет. Бейджи сайдбара обновляй перезапросом `GET /admin/me`
  раз в минуту и после своих действий.
- Интерфейс для экранов оставь прежним (`subscribe(cb)` → `unsubscribe`), чтобы компоненты не менялись. При
  выходе из сессии отключай клиент.

## Определение «готово»

- [ ] В коде нет `client` черновика, `schema.d.ts`, `admin.draft.yaml`, черновых моков и `@stomp/stompjs`.
- [ ] `VITE_USE_MOCKS=false`: ни одного 404 в Network на всех экранах.
- [ ] Под `+996555000011` (`MARKET_ADMIN`) скрыты пункты, табы и кнопки без права, бейджи `null` не показаны,
      `/admin/staff` недоступен.
- [ ] Под `+996555000010` (`SUPER_ADMIN`) телефоны видны полностью. Если снять у роли `PII_VIEW`, появятся маски,
      в том числе в журнале и выгрузках.
- [ ] Каждое действие (одобрить, отклонить, заблокировать, предупредить, написать, решить жалобу, скрыть запрос,
      расширить, публикация карты, справочники, рассылка, сотрудники) работает. Ответ сервера обновляет кэш, а
      запись появляется в «Журнале».
- [ ] Ошибки 400/403/409/429 показывают `detail`, поля валидации подсвечены.
- [ ] Новая заявка из мобильного приложения появляется в A8 без перезагрузки (Centrifugo).
- [ ] Все `export.xlsx` скачиваются с правильным именем файла.
- [ ] После перезагрузки страницы сессия восстанавливается через cookie. «Выйти» стирает её.
- [ ] `npm run typecheck && npm run lint && npm test && npm run build` проходят.

В конце дай отчёт: что переведено, что удалено, какие расхождения с макетом остались, вопросы к бэкенду.
