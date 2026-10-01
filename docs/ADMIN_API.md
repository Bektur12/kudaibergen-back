# API веб-админки «Кудайберген»

Контракт для фронтенда админки. Точные схемы — OpenAPI: `/v3/api-docs` (теги «Админка: …»), Swagger UI —
`/swagger-ui.html`. Этот файл описывает правила, которых в схеме не видно, и что менялось по фазам.

Дизайн `docs/admin-design.html` к бэкенду пока не приложен — API собран по тексту задачи. Когда файл появится,
поля экранов A1–A9 сверяются с ним отдельно.

## Общие правила

- Все пути — под `/api/v1/admin/**`. Даты — ISO 8601 UTC, «2 ч назад» и «вчера» считает фронт.
- **Ошибки** — RFC 7807, как во всём API: `{type, title, status, detail, code, errors?[{field, message}], …}`.
  Ветвиться по `code`, `detail` показывать как есть. Валидация — 400 `VALIDATION_ERROR` с `errors[]`.
  - 401 `UNAUTHORIZED` — нет токена, он истёк, или сотрудника отключили: обновить сессию через
    `/auth/refresh`, не вышло — на экран входа.
  - 403 `FORBIDDEN` — у сотрудника нет нужного права, или это токен мобильного приложения.
  - 404, 409 (конфликт), 429 (`retryAfter` в секундах) — по месту.
- **Права.** Сервер проверяет каждое действие сам. Фронт по `me.permissions` только прячет пункты меню и кнопки.
- **Телефоны** приходят полными или маской `+996 *** ** 34 56` — это решает сервер по праву `PII_VIEW`,
  фронт показывает как есть. Маска действует и в журнале, и в выгрузках.
- **Enum'ы** приходят кодом, подписи RU/KG делает фронт.
- **Списки**: `{items, nextCursor, counts?}` — курсор непрозрачный, `nextCursor = null` значит «дальше ничего нет»;
  `counts` — серверные счётчики табов и чипов (появятся в списках фаз 2–7).
- **CORS**: админка принимается только с доменов из `ADMIN_ORIGINS` (по умолчанию `http://localhost:*`,
  `http://127.0.0.1:*`). Запросы шлются с `credentials: 'include'`, иначе cookie сессии не уйдёт.

## Роли и права

Проверки в коде — только по правам (`@PreAuthorize("hasAuthority('…')")`), роль в коде не сравнивается.
Новая роль (оператор, модератор, контент-менеджер, аналитик) добавляется одной миграцией: значение в CHECK
`admin_members.admin_role` и строки в `admin_role_permissions`.

| Роль | Кто | Права |
| --- | --- | --- |
| `SUPER_ADMIN` | владелец / техадмин | все, всегда (новые права — сразу; таблица для него не читается) |
| `MARKET_ADMIN` | «Администратор рынка» | все, кроме `STAFF_MANAGE`; набор — в `admin_role_permissions`, суперадмин может его менять |

| Право | Что открывает |
| --- | --- |
| `DASHBOARD_VIEW`, `EXPORT_EXCEL` | сводка A1, выгрузки .xlsx |
| `SELLERS_VIEW`, `SELLERS_VERIFY`, `SELLERS_BLOCK`, `DISPUTES_RESOLVE` | продавцы A2: список, проверка, блокировка, споры за контейнер |
| `MASTERS_VIEW`, `MASTERS_VERIFY`, `MASTERS_BLOCK`, `MASTERS_CREATE` | мастера A7, «Добавить вручную» |
| `MARKET_VIEW`, `MARKET_EDIT`, `MARKET_MAP_PUBLISH`, `TENANTS_IMPORT` | рынок и карта A3: просмотр и QR, контейнеры, схема и GPS-точки, импорт арендаторов |
| `REQUESTS_VIEW`, `REQUESTS_MANAGE` | запросы и заявки A8; скрыть, расширить радиус |
| `COMPLAINTS_VIEW`, `COMPLAINTS_RESOLVE`, `CONTENT_REMOVE`, `SELLER_WARN` | модерация A4 |
| `DICTIONARIES_VIEW`, `DICTIONARIES_EDIT` | справочники A5 / A9 |
| `BROADCASTS_VIEW`, `BROADCASTS_SEND` | рассылки A6 |
| `USERS_VIEW`, `USERS_BLOCK` | пользователи |
| `PII_VIEW` | полные телефоны вместо маски |
| `MESSAGE_USERS` | кнопка «Написать» |
| `AUDIT_VIEW` | журнал |
| `STAFF_MANAGE` | сотрудники и права ролей |

## Вход и сессия

Сессия админки отдельная от мобильного приложения. Её access-токен (JWT, audience `admin`, 15 минут) принимается
только на `/api/v1/admin/**`. Токен приложения на админке даёт 403. Refresh-токен живёт 12 часов и лежит
только в httpOnly-cookie `admin_refresh` (`Path=/api/v1/admin/auth`, `SameSite=Strict`, `Secure` в проде).
В JS он недоступен и в теле ответа не приходит.

Права в токене — для удобства фронта. Сервер на каждый запрос берёт текущие права из базы: отключённый сотрудник
теряет доступ сразу (401), снятое право действует сразу (403).

```
POST /api/v1/admin/auth/login     {phone, password}        → 200 {expiresIn, resendIn, debugCode?}   SMS-код ушёл
POST /api/v1/admin/auth/verify    {phone, code}            → 200 AdminSessionDto + Set-Cookie admin_refresh
POST /api/v1/admin/auth/refresh   (cookie)                 → 200 AdminSessionDto + новая cookie (старая гасится)
POST /api/v1/admin/auth/logout    (cookie)                 → 204, cookie стёрта
POST /api/v1/admin/auth/password/code {phone}              → 200 {expiresIn, resendIn, debugCode?}
POST /api/v1/admin/auth/password  {phone, code, password}  → 204, все сессии админки сотрудника закрыты
```

- `AdminSessionDto = {accessToken, expiresIn, me: AdminMeDto}`.
- **Пароль**: 10–72 символа, хотя бы одна буква и одна цифра. Первый пароль и сброс забытого — одним путём:
  `password/code`, затем `password`. Ответ `password/code` одинаковый для любого номера, SMS получает только
  активный сотрудник.
- **Ошибки входа**: 401 `ADMIN_BAD_CREDENTIALS` одинаков для неверного пароля, чужого номера, незаданного пароля
  и отключённого сотрудника. 400 `OTP_INVALID` (`attemptsLeft`) и `OTP_EXPIRED`. 429 `ADMIN_LOGIN_RATE_LIMITED`
  (10 неверных паролей на номер за 15 минут, 30 на IP; удачные входы лимит не тратят), а также `OTP_COOLDOWN`
  (новый код — через 42 с) и `OTP_BLOCKED` (5 неверных кодов — 15 минут).
- `OTP_COOLDOWN` на `login` значит: пароль верный, а код уже ушёл меньше 42 с назад. Фронт переходит к вводу
  кода с отсчётом `retryAfter` до «Отправить ещё раз».
- `debugCode` приходит только в dev (`SMS_EXPOSE_CODE=true`).
- Сценарий фронта: access-токен хранить в памяти. На 401 один раз вызвать `/auth/refresh`, повторить запрос;
  снова 401 — на экран входа. При загрузке страницы — сразу `/auth/refresh`: cookie есть — сессия восстановлена.

TOTP (Google Authenticator) вместо SMS-кода — позже, отдельной задачей.

## Текущий сотрудник

`GET /api/v1/admin/me` (любой сотрудник) → `AdminMeDto`:

```json
{
  "userId": 1, "phone": "+996555000099", "fullName": "Айбек Т.", "title": "Администратор рынка",
  "role": "MARKET_ADMIN", "permissions": ["DASHBOARD_VIEW", "…"],
  "badges": {"shopsPending": 7, "mastersPending": 3, "complaintsNew": 4},
  "lastLoginAt": "2026-09-30T08:12:00Z"
}
```

Бейджи сайдбара: `shopsPending` — продавцы на проверке, новые и переезды; `mastersPending` — мастера на проверке;
`complaintsNew` — жалобы без решения; `disputesOpen` — открытые споры за контейнер. `null` — у сотрудника нет права
на этот раздел.

## Поиск в шапке

`GET /api/v1/admin/search?q=` — любое из прав `USERS_VIEW`, `SELLERS_VIEW`, `MASTERS_VIEW`, `MARKET_VIEW`.

- Ответ: `{users, shops, masters, containers}`, в каждой группе до 5 результатов.
- Группа `null` — на этот раздел у сотрудника нет права. `[]` — ничего не нашлось.
- Телефон ищется по цифрам, от трёх подряд. Магазин и мастер — по подстроке имени или по телефону владельца.
- Контейнер ищется по записи «14 12», «14-12», «Ряд 14 · 12», «р14/12» (ряд + номер).
- Запрос короче двух символов возвращает пустые группы.

## Журнал действий

Любое изменяющее действие админки (POST / PUT / PATCH / DELETE под `/api/v1/admin/**`) пишется в
`admin_audit_log`. Поля: кто, действие, тип и id объекта, состояние до и после (JSON), комментарий, IP, время.

- Строка журнала пишется в одной транзакции с действием. Если действие упало, в журнал ничего не пишется.
- Тест `AdminEndpointsTest` не даёт добавить эндпоинт админки без права или изменяющий эндпоинт без журнала.
- Вход и смена пароля тоже пишутся: `ADMIN_LOGIN`, `ADMIN_PASSWORD_SET`.
- Просмотр — раздел «Журнал» ниже.

Коды действий фазы 1:

| action | entity | когда |
| --- | --- | --- |
| `SHOP_APPROVE`, `SHOP_REJECT`, `SHOP_BLOCK`, `SHOP_UNBLOCK` | SHOP | проверка и блокировка магазина (комментарий — причина) |
| `COMPLAINT_RESOLVE` | COMPLAINT | закрыть жалобу |
| `PART_REMOVE` | PART | удалить запчасть |
| `ROW_CONTAINERS_SET`, `CONTAINER_UPDATE` | ROW, CONTAINER | места в ряду, арендатор контейнера |
| `GEO_ANCHORS_SET`, `MAP_PUBLISH` | MAP | GPS-точки, новая схема |
| `ADMIN_LOGIN`, `ADMIN_PASSWORD_SET` | ADMIN | вход, пароль |
| `SHOP_SMS_CODE`, `SHOP_WARN`, `SHOP_MESSAGE` | SHOP | SMS-код арендатору, предупреждение, «Написать» |
| `DISPUTE_RESOLVE` | DISPUTE | решение спора |
| `COMPLAINT_RESOLVE` | COMPLAINT | решение жалобы (комментарий — из поля «Комментарий для журнала и для продавца») |
| `PART_REQUEST_WIDEN`, `PART_REQUEST_HIDE`, `SERVICE_REQUEST_WIDEN`, `SERVICE_REQUEST_HIDE` | запросы | мониторинг A8 |
| `TENANTS_IMPORT_PREVIEW`, `TENANTS_IMPORT_APPLY` | TENANT_IMPORT | загрузка и применение списка (в журнал — итог, без строк) |
| `BROADCAST_CREATE`, `BROADCAST_UPDATE`, `BROADCAST_SCHEDULE`, `BROADCAST_CANCEL` | BROADCAST | рассылки |
| `USER_BLOCK`, `USER_UNBLOCK`, `USER_MESSAGE` | USER | пользователи |
| `STAFF_CREATE`, `STAFF_UPDATE`, `ROLE_PERMISSIONS_SET` | STAFF, ROLE | сотрудники и права |
| `EXPORT_XLSX`, `DASHBOARD_REFRESH` | EXPORT, DASHBOARD | выгрузки, пересчёт сводки |
| `MAP_DRAFT_SAVE`, `MAP_DRAFT_DISCARD`, `MAP_PUBLISH`, `CONTAINER_CREATE`, `CONTAINER_DELETE` | MAP, CONTAINER | черновик и публикация схемы, места |
| `BRAND_*`, `MODEL_*`, `CATEGORY_*`, `SERVICE_TYPE_*` (код услуги — в комментарии), `SYNONYM_*`, `HINT_*`, `MEDIA_UPLOAD` | справочники | создание, правка, удаление, порядок, логотип |
| `MASTER_CREATE`, `MASTER_APPROVE`, `MASTER_REJECT`, `MASTER_BLOCK`, `MASTER_UNBLOCK`, `MASTER_WARN`, `MASTER_MESSAGE` | MASTER | мастера |

## Продавцы [A2]

### Список

`GET /admin/shops?tab=ALL|PENDING|DISPUTES|BLOCKED|REJECTED&q=&cursor=&limit=` — право `SELLERS_VIEW`.
Ответ — `{items: AdminShopRow[], nextCursor, counts: {all, pending, disputes, blocked, rejected}}`.

- Порядок: новые сверху.
- `counts` считаются по всем табам с учётом `q`, но без учёта выбранного таба.
- `q` — название, цифры телефона владельца или «14 12» (ряд + номер).
- Табы: `PENDING` — новые магазины и переезды на проверке; `DISPUTES` — с открытым спором; `REJECTED` — отклонённые.

`AdminShopRow` — колонки таблицы:

| поле | что |
| --- | --- |
| `name`, `avatarUrl?` | магазин |
| `ownerPhone` | телефон владельца (маска без `PII_VIEW`) |
| `location`, `pendingLocation?` | где стоит и куда переезжает (`LocationDto`: `rowLabel`, `number`, `side`…) — «Ряд 14 · 12» |
| `tenantMatch` | сверка со списком арендаторов, см. ниже |
| `submittedAt` | когда подал на проверку (последняя заявка, иначе регистрация) |
| `status` | `PENDING_VERIFICATION` / `ACTIVE` / `BLOCKED` / `REJECTED` |
| `warned` | действует предупреждение (90 дней) — «Предупреждён» |
| `openDisputes` | открытых споров с участием магазина |

`tenantMatch` считается для контейнера, который проверяется (при переезде — новое место). Значения перечислены по
приоритету:

| значение | подпись | когда |
| --- | --- | --- |
| `CONTAINER_TAKEN` | Контейнер занят | открыт спор за контейнер с участием магазина |
| `SMS_CONFIRMED` | Подтверждён по SMS | арендатор ввёл SMS-код |
| `IN_TENANT_LIST` | В списке аренды | телефон арендатора по базе рынка совпадает с телефоном владельца или сотрудника |
| `NOT_IN_LIST` | Нет в списке | телефон арендатора есть, но другой |
| `AWAITING_CHECK` | Ждёт сверки | телефона арендатора в базе нет |

### Карточка

`GET /admin/shops/{id}` → `AdminShopDetailDto` (правая панель). В карточке:

- владелец: `owner {userId, name, phone}`, телефон магазина;
- место: `location`, `pendingLocation`;
- арендатор по списку: `tenant {name, phone, matches}` («Токтосунов А. · совпадает») и `tenantMatch`;
- марки, категории, часы работы, рейтинг, число товаров и людей в боксе;
- история проверок (`verifications`), открытые споры;
- для решения о санкциях: `complaints90d`, `warnings90d` и история санкций `sanctions`.

`statusReason` — причина блокировки или отказа.

### Действия

Каждое действие возвращает обновлённую карточку и пишется в журнал (до и после).

| эндпоинт | право | что делает |
| --- | --- | --- |
| `POST /admin/shops/{id}/approve` | `SELLERS_VERIFY` | новый или отклонённый магазин становится действующим, переезд занимает новое место. 409 `NOTHING_TO_VERIFY`, `CONTAINER_TAKEN` |
| `POST /admin/shops/{id}/reject {reason}` | `SELLERS_VERIFY` | новый магазин → `REJECTED`: скрыт, контейнер свободен, продавец видит причину и выбирает место заново. Переезд — отменяется. 409 `ALREADY_REJECTED` |
| `POST /admin/shops/{id}/send-sms-code` | `SELLERS_VERIFY` | SMS-код на телефон арендатора проверяемого контейнера; продавец вводит его в приложении. Ответ `{sentTo, expiresIn, resendIn, debugCode?}`. 409 `SMS_UNAVAILABLE` |
| `POST /admin/shops/{id}/block {reason}` | `SELLERS_BLOCK` | скрыт, запросы не приходят. 409 `ALREADY_BLOCKED`, `SHOP_REJECTED` |
| `POST /admin/shops/{id}/unblock` | `SELLERS_BLOCK` | 409 `NOT_BLOCKED` |
| `POST /admin/shops/{id}/warn {reason}` | `SELLER_WARN` | предупреждение на 90 дней |
| `POST /admin/shops/{id}/message {text}` | `MESSAGE_USERS` | пуш всем людям бокса → `{recipients}` |

Продавец получает пуш. При решениях по месту и блокировке это `ACCOUNT_STATUS` (`event`: `APPROVED`, `REJECTED`,
`BLOCKED`, `UNBLOCKED`). При предупреждении и сообщении — `ADMIN_MESSAGE` (`kind`: `WARNING` или `MESSAGE`).

### Споры за контейнер

Спор открывает человек, чьё место в приложении занято чужим магазином: «Это мой контейнер»,
`POST /api/v1/market/containers/{id}/claim`. Магазина у заявителя может не быть.

- `GET /admin/disputes?status=OPEN|RESOLVED&cursor=` (`SELLERS_VIEW`) → `{items: AdminDisputeDto[], nextCursor,
  counts: {open, resolved}}`.
- `GET /admin/disputes/{id}` — один спор.
- В споре видны обе стороны (`claimant`, `current`: человек, телефон, магазин) и арендатор контейнера по базе рынка.
  `tenantSide` подсказывает, на чьей стороне арендатор по телефону: `CLAIMANT` / `CURRENT` / `NONE` / `UNKNOWN`.
- `POST /admin/disputes/{id}/resolve {winner: CURRENT|CLAIMANT, comment?}` (`DISPUTES_RESOLVE`):
  - `CURRENT` — всё остаётся как есть.
  - `CLAIMANT` — стоявший магазин отклоняется и освобождает место. Магазин заявителя, если он есть, встаёт в
    контейнер сразу. Телефон заявителя становится телефоном арендатора — без магазина он регистрируется на этом
    месте обычным путём.
  - Обеим сторонам уходит пуш `DISPUTE_RESOLVED`. 409 `DISPUTE_RESOLVED` — спор уже решён.

В задаче было `{winnerShopId}`. Сделано `winner`, потому что у заявителя часто нет магазина: его место занято.

### Импорт арендаторов

Право `TENANTS_IMPORT`.

- `GET /admin/tenants/import/template.xlsx` — шаблон.
- `POST /admin/tenants/import` (multipart, поле `file`, xlsx или csv до 5 МБ) — предпросмотр, ничего не меняет.
  Колонки: ряд («14» или «Ряд 14»), номер контейнера, сторона (С / Ю / З / В, можно пусто, если номер в ряду один),
  ФИО, телефон (любая запись: 0555 12 34 56, +996…).
- Ответ `TenantImportDto {id, status: PREVIEW, rowsTotal, rowsOk, rowsError, changed, changes[≤200], errors[{line, message}]}`.
  `changes` — что изменится: было и стало по ФИО и телефону.
- `GET /admin/tenants/import/{id}` — тот же отчёт.
- `POST /admin/tenants/import/{id}/apply` — записывает ФИО и телефоны в контейнеры, строки с ошибками пропускаются.
  409 `IMPORT_APPLIED`, `IMPORT_EXPIRED` (предпросмотр действует 24 часа).
- Пустая ячейка ФИО или телефона значение не стирает.
- Сам файл не хранится — только разобранные строки. Поэтому вместо `file_key` из задачи — `file_name`.

## Мастера [A7]

`GET /admin/masters?tab=ALL|PENDING|MOBILE|BLOCKED|REJECTED&service=&brandId=&q=&cursor=` (`MASTERS_VIEW`) →
`{items: AdminMasterRow[], nextCursor, counts: {all, pending, mobile, blocked, rejected}}`.

- Фильтры: `service` — код услуги; `brandId` — работает с маркой, включая мастеров «все марки»;
  `q` — название, адрес, цифры телефона.
- Колонки строки: мастер или СТО, `ownerPhone`, `phone`, `services` (коды; названия — `GET /service-types`).
- Место: `address`, `mobile`, `radiusKm`. Подпись собирает фронт: «Садыгалиева 41 · 5 км» или «выезд · 10 км».
- `check` — колонка «Проверка»: `COMPLAINT` (есть жалоба без решения), `NO_PHOTOS` или `PHOTOS`.
  «Документы» появятся вместе с загрузкой документов мастера.
- Ещё в строке: `status`, `warned`, рейтинг.

`GET /admin/masters/{id}` — карточка. В ней профиль целиком, фото и статистика за 30 дней (`stats30d`: пришло
заявок, откликов «Могу помочь», «Договорились»). Ещё жалобы (`openComplaints`, `complaints90d`), предупреждения
и история санкций.

| эндпоинт | право | что делает |
| --- | --- | --- |
| `POST /admin/masters` | `MASTERS_CREATE` | «+ Добавить вручную»: `{ownerPhone, ownerName?, profile: <тело POST /masters из приложения>}`. Пользователь создаётся, если его нет; режим его приложения не меняется; профиль сразу `ACTIVE`. 201 + карточка; 409 `ALREADY_MASTER` |
| `POST /admin/masters/{id}/approve` | `MASTERS_VERIFY` | на проверке или отклонён → действует. 409 `NOTHING_TO_VERIFY` |
| `POST /admin/masters/{id}/reject {reason}` | `MASTERS_VERIFY` | `REJECTED`: заявки не приходят, мастер видит причину |
| `POST /admin/masters/{id}/block {reason}`, `/unblock` | `MASTERS_BLOCK` | 409 `ALREADY_BLOCKED` / `NOT_BLOCKED` |
| `POST /admin/masters/{id}/warn {reason}` | `MASTERS_BLOCK` | предупреждение на 90 дней. Отдельного права «предупредить мастера» в задаче нет |
| `POST /admin/masters/{id}/message {text}` | `MESSAGE_USERS` | пуш `ADMIN_MESSAGE` |

## Справочники [A5, A9]

Права: `DICTIONARIES_VIEW` — смотреть, `DICTIONARIES_EDIT` — менять. Все пути — под `/api/v1/admin/dictionaries`.
Списки отдаются целиком (`nextCursor = null`), `counts` — по табам «все / действуют / скрыты».

- **Скрытие вместо удаления.** `active = false` убирает запись из списков приложения: марки, модели, категории,
  плитки услуг, подсказки. То, что уже выбрано (машины в гаражах, марки магазинов, старые заявки), продолжает
  работать. Скрытую услугу нельзя выбрать в новой заявке (400 `SERVICE_HIDDEN`) и мастеру.
- **Удаление** возможно, только если запись нигде не используется. Иначе 409 `IN_USE`, а в `detail` — где она
  используется: «Марка используется: моделей 19, продавцов 13, мастеров 2, машин 4».
- **Изменения сразу видны в приложении.** Растёт `GET /api/v1/dictionaries/version` → `{version, updatedAt}`
  (публичный). У публичных списков (`/brands`, `/brands/{id}/models`, `/models`, `/categories`, `/service-types`,
  `/requests/hints`) есть `ETag`: повтор с `If-None-Match` получает 304. Кэши сервера перечитываются после
  коммита правки.

| что | эндпоинты |
| --- | --- |
| марки | `GET /brands?q=` (со счётчиками `modelsCount`, `sellersCount`, `mastersCount`, `carsCount`), `GET /brands/{id}`, `POST /brands`, `PATCH /brands/{id}`, `DELETE /brands/{id}`, `PUT /brands/order {ids}` |
| логотип | `POST /api/v1/admin/media/photos` (multipart `file`) → `id`; `PUT /brands/{id}/logo {mediaId}`, `DELETE /brands/{id}/logo`. В приложении `logoUrl = /api/v1/brands/{id}/logo` — постоянная ссылка с редиректом на файл. Картинка пережимается в JPEG: прозрачный фон станет белым |
| модели | `GET /brands/{brandId}/models?q=`, `POST /brands/{brandId}/models`, `PATCH /models/{id}`, `DELETE /models/{id}`. `displayName` заменяет подпись «модель + поколение» в приложении. В `PATCH` пустая строка стирает `generation` и `displayName`, `null` — не менять |
| категории | `GET /categories`, `POST /categories {slug, nameRu, nameKg}` (встаёт в конец), `PATCH /categories/{id}`, `DELETE /categories/{id}`, `PUT /categories/order {ids}` |
| услуги [A9] | `GET /service-types` (с `mastersCount`, `requests30d`), `POST /service-types`, `PATCH /service-types/{code}`, `DELETE /service-types/{code}`, `PUT /service-types/order {codes}` (все коды по порядку, drag-and-drop) |
| синонимы | `GET /synonyms?q=`, `POST /synonyms {term, synonym, bidirectional = true}` → созданные пары, `DELETE /synonyms/{id}` |
| подсказки | `GET /hints` (с `requestsCount`), `POST /hints`, `PATCH /hints/{id}` (`categoryId = 0` — убрать категорию), `DELETE /hints/{id}` |

Поля услуги в правой панели [A9]: названия RU / KG, `icon`, `needsLocation` («точка на карте»), `urgent`
(«Срочно»), `defaultDuration` («ожидание»: `MIN_15` / `MIN_30` / `HOUR_1` / `HOUR_3`), `defaultRadiusKm` — радиус
заявки, если клиент его не выбрал. «Скрыть услугу» — `active = false`.

Алиасы («мерс, мерседес») хранятся массивом у марки и модели; публичный поиск марок и моделей ими пользуется.
`PATCH` с `aliases` заменяет весь список.

## Рынок и карта [A3]

Пути — под `/api/v1/admin/market`. Права: `MARKET_VIEW` — смотреть и печатать QR, `MARKET_EDIT` — контейнеры и
черновик, `MARKET_MAP_PUBLISH` — публикация схемы и GPS-точки.

**Схема: черновик → публикация.**

- `GET /map` → `{current, published, draft?}`:
  - `current` — что видят приложения: «версия 7 · опубликована 21.09», кто опубликовал, рядов, масштаб, привязка
    к GPS;
  - `published` — та же схема в формате загрузки, с неё начинается редактирование;
  - `draft` — черновик: `{data, basedOnVersion, outdated, valid, error?, updatedAt, updatedBy}`.
- `PUT /map/draft` — сохранить черновик. Формат — `market-map.json` плюс ряды с кодами, как у прямой публикации.
  Схема с ошибкой сохраняется, а ошибка видна в `draft.error`. Приложения черновик не видят.
- `DELETE /map/draft` — удалить черновик.
- `POST /map/publish {comment?}` — новая версия из черновика, после чего черновик удаляется. Тело
  необязательно; `comment` («добавлен ряд 31») пишется в журнал. `GET /api/v1/market/map` сразу
  отдаёт новый `version`, и приложения перекачивают карту.
  - Ряды сопоставляются по коду: контейнеры и магазины остаются на своих рядах, ряды, которых нет в схеме,
    выключаются.
  - 400 `BAD_MAP`, 409 `NO_DRAFT`.
- `PUT /map` — опубликовать сразу, без черновика (как раньше).
- У улиц (`streets`) `name`, `labelX`, `labelY` могут быть `null` — улица без подписи (безымянный проезд):
  рисуется только полигон. Это не ошибка данных.

Редактор MVP — JSON схемы с SVG-превью на фронте: в `data` есть всё для отрисовки (`boundary`, `blocks`, `passages`,
`entrances`, `rows[].geometry`). Визуальный редактор полигонов — отдельная задача. В задаче `market_map_version`
хранит `data` и статус `DRAFT / PUBLISHED`. Здесь опубликованные версии лежат в `map_versions` (как было, плюс
`published_by`), а черновик — в `map_drafts`, один на рынок.

**Ряды и места.**

| эндпоинт | что |
| --- | --- |
| `GET /rows?includeInactive=` | ряды со счётчиками: `containers`, `withShop`, `free`, `disabled`, `bySide` («Контейнеров 16 · С продавцом 13 · Свободно 3 · Северная 8 · Южная 8»); `counts` — по всему рынку |
| `GET /rows/{id}` | ряд и сетка мест по сторонам; в клетке — номер, позиция, арендатор (ФИО, телефон), кто стоит (`shop`), кто переезжает сюда (`incoming`), QR |
| `PUT /rows/{id}/containers {counts}` | число мест на сторонах (недостающие создаются, лишние выключаются) |
| `POST /containers {rowId, side, number, posInRow?, tenantName?, tenantPhone?}` | новое место. 409 `CONTAINER_EXISTS`, 400 `WRONG_SIDE` |
| `PATCH /containers/{id}` | арендатор (`tenantName`, `tenantPhone`; пустая строка — убрать), перенумерация (`side`, `number`, `posInRow`), `active` |
| `DELETE /containers/{id}` | только пустое место без истории. 409 `CONTAINER_OCCUPIED` — стоит магазин; `CONTAINER_IN_USE` — есть история, выключите |
| `GET /qr/{token}.png`, `GET /qr/sheet?rowId=` | QR и лист наклеек |
| `GET /geo-anchors`, `PUT /geo-anchors {anchors}` | опорные GPS-точки; PUT заменяет набор и пересчитывает привязку |

## Модерация [A4]

Жалобы приходят из приложений: `POST /api/v1/complaints {type, targetId, reason, text?, relatedRequestId?,
relatedServiceRequestId?}`.

- Типы: `PART`, `SHOP`, `SHOP_PHOTO` (targetId — id фото), `REVIEW`, `MASTER_REVIEW`, `CHAT`, `CHAT_MESSAGE`,
  `MASTER`, `SERVICE_OFFER`.
- Причины: `FAKE_ORIGINAL` («Подделка под оригинал»), `REVIEW_WITHOUT_PURCHASE`, `SPAM_FRAUD`, `WRONG_PLACE`,
  `RUDE`, `OTHER`.
- Повторная жалоба на то же самое не дублируется. Не больше 20 жалоб в сутки.

Эндпоинты админки:

- `GET /admin/complaints?status=OPEN|RESOLVED|REJECTED&type=&cursor=` (`COMPLAINTS_VIEW`) — лента. В строке: тип,
  причина, объект («Фара передняя правая Depo», «Отзыв ★1», текст сообщения), ответственный, заявитель (телефон —
  маской без `PII_VIEW`), время. `counts`: `{open, resolved, rejected}`.
- `GET /admin/complaints/{id}` — карточка:
  - `subject` — объект: фото, цена, машины у запчасти, звёзды у отзыва, скрыт ли;
  - `party` — ответственный (магазин с местом, мастер или пользователь — автор отзыва или собеседник в чате);
  - `partyStats` за 90 дней: `complaints90d`, `warnings90d`, `removed90d`;
  - заявитель и связанный запрос;
  - `sameTargetOpen` — сколько ещё открытых жалоб на этот же объект.
- `POST /admin/complaints/{id}/resolve {action, comment}` (`COMPLAINTS_RESOLVE`, плюс право на само действие):

| action | право | что происходит |
| --- | --- | --- |
| `REMOVE_CONTENT` | `CONTENT_REMOVE` | объект скрывается (см. ниже); ответственному — пуш с комментарием. Магазин, мастер и чат целиком не скрываются — 400 `NOTHING_TO_REMOVE` |
| `WARN_SELLER` | `SELLER_WARN` | предупреждение ответственному на 90 дней, пуш |
| `BLOCK_SHOP` | `SELLERS_BLOCK` / `MASTERS_BLOCK` / `USERS_BLOCK` | блокировка ответственного: магазина, мастера или пользователя |
| `UNFOUNDED` | — | жалоба отклонена (`REJECTED`) |

Все открытые жалобы на тот же объект закрываются этим же решением, заявители получают пуш `COMPLAINT_RESOLVED`.
Ответ: `{complaint, resolvedTogether}`.

**Скрытие контента** (`hidden_by_admin`):

- запчасть уходит в архив, её нельзя опубликовать или вернуть из архива — 409 `PART_HIDDEN`;
- отзыв пропадает из списка, рейтинг и число отзывов пересчитываются без него;
- сообщение отдаётся без текста и вложений с `code = HIDDEN_BY_ADMIN`;
- отклик мастера пропадает у клиента;
- фото места убирается из профиля.

**Блокировка пользователя:** закрываются все его сессии, его магазин и профиль мастера блокируются вместе с ним,
снятие блокировки возвращает их.

## Запросы и заявки [A8]

Права: `REQUESTS_VIEW` — смотреть, `REQUESTS_MANAGE` — «Расширить» и «Скрыть».

- `GET /admin/part-requests?period=TODAY|WEEK|MONTH|ALL&status=&noReplies=&q=&cursor=` и
  `GET /admin/service-requests?…&service=` — таблицы.
  - Колонки: текст; покупатель (телефон маской) и время; машина; `received / seen / positive` — «получили /
    посмотрели / могут».
  - Статус: `ACTIVE` («Активна»), `AGREED` («Договорились»), `NO_REPLIES` («Без откликов»), `EXPIRED` («Время
    вышло»), `CLOSED`, `HIDDEN`.
  - `counts`: `{parts, services, partsNoReplies, servicesNoReplies}` за период — «Запросы на запчасти · 342»,
    «Заявки на услуги · 57», «Без откликов · 6».
  - Период считается по Бишкеку. `q` — текст или цифры телефона.
- `GET /admin/part-requests/{id}` — запрос: машина, текст, фото, кому ушёл, счётчики, ответы продавцов (цена,
  состояние, через сколько минут ответили).
- `GET /admin/service-requests/{id}` — заявка: машина снимком (объём, топливо, страна), описание и вложения,
  когда / где / радиус, счётчики, отклики (мастер, через сколько ответил, когда может, цена «от»).
- `POST /admin/part-requests/{id}/widen` — до всего рынка; `POST /admin/service-requests/{id}/widen` — радиус
  +5 км. Новым адресатам уходит пуш, срок начинается заново.
- `POST /admin/part-requests/{id}/hide {reason}`, `POST /admin/service-requests/{id}/hide {reason}` — запрос
  закрывается и пропадает из лент продавцов и мастеров, покупателю — пуш с причиной.
- **Живое обновление.** `GET /admin/realtime/token` → `{connectionToken, subscriptionToken, channel:
  "admin:requests"}`. В канале — `{type: REQUEST_CHANGED, payload: {kind: PART|SERVICE, id, event}}`, где `event`:
  `DISPATCHED`, `REPLY`, `EXPIRED`, `CLOSED`, `HIDDEN`. В задаче был STOMP, но живые события в проекте идут через
  Centrifugo; в его конфиге добавлено пространство `admin`.

## Сводка [A1]

Право `DASHBOARD_VIEW`. Агрегаты кэшируются на 90 секунд. В каждом ответе есть `generatedAt` — по нему
пишется «данные обновлены 2 мин назад». `POST /admin/dashboard/refresh` пересчитывает сводку сразу. Дни
считаются по Бишкеку.

| эндпоинт | что |
| --- | --- |
| `GET /admin/dashboard/kpi` | `requestsToday` и `requestsDeltaPct` — % к среднему за прошлые 7 дней; `haveIn30Pct` — доля запросов за 7 дней с «Есть» за 30 минут, `haveIn30DeltaPp` — в п.п. к прошлой неделе; `activeShops` и `activeShopsNewWeek`; `serviceRequestsToday` и `serviceDeltaPct` — % к вчера. `null` — не с чем сравнить |
| `GET /admin/dashboard/requests-by-day?range=D14\|D30\|QUARTER` | точки `{date, requests, withHave, serviceRequests}`, пустые дни — нули |
| `GET /admin/dashboard/unanswered-by-brand?period=WEEK\|MONTH` | марки от 3 запросов: `requests`, `unanswered` (без «Есть» за 30 минут), `pct`, `sellers` (действующих боксов с маркой), по убыванию `pct`. `hint` — у худшей марки от 20%: готовый `text` («Lada: 80% запросов без ответа за 30 минут — её продают всего 2 бокса…») и `broadcastBrandIds` для кнопки «Сделать рассылку» (аудитория SELLERS, фильтр марок) |
| `GET /admin/dashboard/top-searched?period=WEEK\|MONTH` | топ-10 «Чаще всего ищут»: по подсказке «Что ищем?», иначе по тексту запроса; `requests`, `havePct` |
| `GET /admin/dashboard/pending` | «Ждут действий»: `shops`, `masters`, `complaints`, `disputes` и `latest` — 10 свежих `{kind, id, title, phone, createdAt}`; `title` — готовый текст («Чат · Спам / мошенничество», «Ряд 14 · 12») |

## Выгрузки Excel

`GET …/export.xlsx` — те же фильтры, что у списка, все страницы (до 10 000 строк). Права: `EXPORT_EXCEL` плюс
право на сам раздел. Телефоны выгружаются с учётом `PII_VIEW`. Каждая выгрузка пишется в журнал
(`EXPORT_XLSX`, в комментарии — что выгружено, в `after` — сколько строк).

| файл | эндпоинт |
| --- | --- |
| сводка (KPI, по дням за 30 дней, без ответа по маркам, чаще всего ищут) | `GET /admin/dashboard/export.xlsx` |
| продавцы | `GET /admin/shops/export.xlsx?tab=&q=` |
| мастера | `GET /admin/masters/export.xlsx?tab=&q=&service=&brandId=` |
| запросы на запчасти | `GET /admin/part-requests/export.xlsx?period=&status=&noReplies=&q=` |
| заявки на услуги | `GET /admin/service-requests/export.xlsx?period=&status=&noReplies=&service=&q=` |

Ответ — файл с `Content-Disposition: attachment; filename="prodavcy-2026-10-01.xlsx"`. Фронт скачивает его с тем
же Bearer-токеном: `fetch` → `blob` → ссылка.

## Рассылки [A6]

Права: `BROADCASTS_VIEW` — смотреть и считать аудиторию, `BROADCASTS_SEND` — создавать, планировать и отменять.

- `GET /admin/broadcasts/estimate?audience=ALL|BUYERS|SELLERS|MASTERS&brandIds=&rowIds=&serviceTypes=` →
  `{recipients, withDevices, label: "Получат 64 продавца"}`.
  - Фильтры: `brandIds` — продавцы и мастера этих марок, покупатели с машиной этой марки; `rowIds` — только
    продавцы; `serviceTypes` — только мастера. Неподходящий фильтр — 400 `BAD_FILTER`.
  - Аудитория: только незаблокированные пользователи. Продавцы — люди действующих боксов; мастера — действующие
    профили; покупатели — в режиме «покупатель».
- `POST /admin/broadcasts {audience, filters, titleRu, titleKg?, bodyRu, bodyKg?}` → черновик.
  `PATCH /admin/broadcasts/{id}` — только черновик, иначе 409 `BROADCAST_NOT_DRAFT`.
- `POST /admin/broadcasts/{id}/schedule {at}` или `{now: true}`. Время в тихих часах 22:00–07:00 (Бишкек)
  переносится на 07:00, тогда в ответе `deferred = true`.
- `POST /admin/broadcasts/{id}/cancel` — черновик, запланированную или идущую: оставшиеся получатели её не
  получат. Завершённую отменить нельзя — 409 `BROADCAST_FINISHED`.
- `GET /admin/broadcasts?status=&cursor=` — история: заголовок, кому, `recipients`, `delivered` (у кого есть
  приложение с пушами), `opened`, `openedPct`. `counts`: `{all, drafts, scheduled, sent}`. `GET /admin/broadcasts/{id}`.
- **Отправка — фоновая задача.**
  - Раз в 20 секунд запланированные к этому времени фиксируют получателей и уходят батчами по 500.
  - Текст — на языке получателя; если KG не задан, уходит русский.
  - Пуш: `{type: BROADCAST, broadcastId}`. В тихие часы отправка стоит и продолжается в 07:00.
- **Открытие.** Приложение по нажатию на пуш вызывает `POST /api/v1/broadcasts/{id}/opened`. Каждый получатель
  засчитывается один раз.

## Пользователи

`USERS_VIEW` — смотреть, `USERS_BLOCK` — блокировать, `MESSAGE_USERS` — «Написать».

- `GET /admin/users?role=ALL|BUYER|SELLER|MASTER|BLOCKED&q=&cursor=` — список:
  - колонки: телефон, имя, роли (`BUYER` всегда, плюс `SELLER` и `MASTER`), текущий режим, дата регистрации,
    последний вход, число запросов и заявок, блокировка;
  - `q` — имя или цифры телефона;
  - `counts`: `{all, buyers, sellers, masters, blocked}`.
- `GET /admin/users/{id}` — карточка: гараж, бокс (с ролью в нём), профиль мастера, последние запросы и заявки,
  жалобы от пользователя и на его отзывы и сообщения, санкции.
- `POST /admin/users/{id}/block {reason}` — сессии закрываются, вход запрещён, бокс и профиль мастера
  блокируются вместе с аккаунтом. 409 `ALREADY_BLOCKED`, `SELF_BLOCK`, `STAFF_BLOCK` — активного сотрудника
  админки так не заблокировать: сначала его отключают в «Сотрудниках». Это же действует на «Заблокировать» из жалобы.
- `POST /admin/users/{id}/unblock` — возвращает и то, что было заблокировано вместе с аккаунтом.
- `POST /admin/users/{id}/message {text}` — пуш `ADMIN_MESSAGE` → `{recipients}`, как у магазина и мастера.

## Журнал

`AUDIT_VIEW`.

- `GET /admin/audit?adminId=&action=&entityType=&entityId=&from=&to=&cursor=` — новые сверху: кто (имя
  сотрудника), действие, объект, комментарий, IP, время. `counts`: `{today, week}`.
- `GET /admin/audit/facets` → `{actions, entityTypes}` — для фильтров.
- `GET /admin/audit/{id}` — запись с `before` и `after` (JSON объекта; у действий без объекта — `null`).

## Сотрудники

`STAFF_MANAGE` — по умолчанию только у `SUPER_ADMIN`.

- `GET /admin/staff` — сотрудники: роль, подпись, активен ли, задан ли пароль (`hasPassword`), последний вход.
- `POST /admin/staff {phone, fullName, title?, role}` — пользователь создаётся, если его нет. Пароль сотрудник
  задаёт сам через «Задать пароль» на экране входа (SMS-код). 409 `STAFF_EXISTS`.
- `PATCH /admin/staff/{userId} {role?, title?, fullName?, isActive?}` — смена роли или отключение сразу закрывает
  сессии сотрудника. Себя и последнего активного `SUPER_ADMIN` отключить или понизить нельзя: 409 `STAFF_SELF`,
  `LAST_SUPER_ADMIN`.
- `GET /admin/staff/roles` → `{roles: [{role, defaultTitle, editable, permissions}], allPermissions}`.
- `PUT /admin/staff/roles/{role}/permissions {permissions}` — права роли целиком; действуют сразу. У
  `SUPER_ADMIN` все права всегда, их не меняют — 400 `ROLE_FIXED`.

## Эндпоинты, которые уже были, — теперь по правам

| Эндпоинт | Право |
| --- | --- |
| `DELETE /admin/parts/{id}` | `CONTENT_REMOVE` |
| `PUT /admin/market/rows/{id}/containers`, `PATCH /admin/market/containers/{id}` | `MARKET_EDIT` |
| `GET /admin/market/qr/{token}.png`, `GET /admin/market/qr/sheet?rowId=` | `MARKET_VIEW` |
| `PUT /admin/market/geo-anchors`, `PUT /admin/market/map` | `MARKET_MAP_PUBLISH` |

Эндпоинты карты дополнены разделом A3 (выше).

## Первый суперадмин

При старте, если активного `SUPER_ADMIN` нет и задан `ADMIN_BOOTSTRAP_PHONE`, этот номер становится суперадмином.
Пользователь создаётся, если его нет; имя берётся из `ADMIN_BOOTSTRAP_NAME`. Пароль суперадмин задаёт сам:
`POST /admin/auth/password/code`, затем `POST /admin/auth/password`. Остальных сотрудников заводит суперадмин
(`POST /admin/staff`).

## История изменений

### Фаза 1 — доступ

- Таблицы `admin_members`, `admin_role_permissions`, `admin_audit_log` (миграция V14).
- `users.admin_role` перенесён в `admin_members` и удалён. Роль `SUPERADMIN` переименована в `SUPER_ADMIN`.
- У `refresh_tokens` появилась `audience` (`APP` / `ADMIN`): токен одной сессии не обменивается в другой.
- Вход по паролю и SMS-коду, refresh в cookie, выход, пароль по SMS-коду.
- `GET /admin/me` с бейджами, `GET /admin/search`, журнал и маскирование телефонов.
- Существующие эндпоинты админки переведены на права и пишутся в журнал.
- Токен мобильного приложения больше не открывает админку, даже если пользователь — сотрудник.
- `GET /me` мобильного приложения: `adminRole` теперь `SUPER_ADMIN` / `MARKET_ADMIN`. В приложении это только
  признак.

### Фаза 2 — продавцы и мастера

- Миграция V15:
  - статус `REJECTED` у магазинов и мастеров. Отклонённый магазин не держит контейнер: уникальность места теперь
    только среди неотклонённых;
  - `containers.tenant_name`;
  - таблицы `tenant_imports`, `container_disputes`, `sanctions`;
  - тип жалобы `MASTER`.
  - Открытые жалобы `CONTAINER_CLAIM` перенесены в споры.
- Список продавцов теперь с табами и `counts`: `GET /admin/shops?tab=` вместо `?status=`.
  `GET /admin/shops/verification-queue` убран — вместо него таб `PENDING`.
- Новое: карточка продавца, `send-sms-code`, `warn`, `message`; споры; импорт арендаторов; раздел мастеров.
- Бейдж `disputesOpen` в `/admin/me`.
- Мобильное приложение:
  - статус `REJECTED` у магазина и мастера;
  - пуши `ADMIN_MESSAGE`, `ACCOUNT_STATUS`, `DISPUTE_RESOLVED`;
  - «Это мой контейнер» отвечает 409 `CONTAINER_FREE` и 400 `OWN_CONTAINER`.
  - Всё это описано в `BACKEND_SPEC.md` и `FRONTEND_PROMPT.md`.
- Блокировка пользователя целиком (`users.blocked_at`, `blocked_reason`) перенесена в фазу 7, к разделу
  «Пользователи».

### Фаза 3 — справочники

- Миграция V17:
  - `is_active` у марок, моделей, категорий, услуг и подсказок;
  - `brands.logo_media_id`, `models.display_name`, `service_types.default_radius_km`;
  - `id` у синонимов;
  - назначение медиа `BRAND`;
  - таблица `dictionary_version`.
- Приложение:
  - скрытое не попадает в публичные списки;
  - новый `GET /api/v1/dictionaries/version` и `ETag` у списков справочников;
  - радиус заявки на услугу по умолчанию берётся из справочника;
  - у загруженного логотипа `logoUrl = /api/v1/brands/{id}/logo`.
- `brand_alias` из задачи не нужен: алиасы уже хранятся массивом `brands.aliases`, и поиск ими пользуется.

### Фаза 4 — рынок и карта

- Миграция V18: `map_versions.published_by`, таблица `map_drafts`.
- Черновик и публикация схемы, ряды со счётчиками и сеткой мест, создание и удаление контейнеров, просмотр
  GPS-точек.
- `PATCH /admin/market/containers/{id}` принимает `tenantName`, `side`, `number`, `posInRow`.
  В `AdminContainerDto` добавлены `posInRow` и `tenantName`.

### Фаза 5 — запросы, заявки, модерация

- Миграция V19:
  - `hidden_by_admin` и `hidden_reason` у запчастей, отзывов, отзывов о мастерах, сообщений, запросов, заявок
    и откликов;
  - жалобы: причина, роль заявителя, связанный запрос или заявка, исход; тип `PHOTO` переименован в `SHOP_PHOTO`;
  - `users.blocked_at` и `blocked_reason`.
- `GET /admin/complaints` и `POST /admin/complaints/{id}/resolve` заменены новым контрактом (выше).
- Приложение:
  - новый `POST /api/v1/complaints`;
  - скрытое не видно в поиске, отзывах и лентах;
  - у сообщения появился `code = HIDDEN_BY_ADMIN`;
  - пуши `COMPLAINT_RESOLVED` и `ADMIN_MESSAGE` с `kind = CONTENT_REMOVED`.

### Фаза 6 — сводка и выгрузки

- Сводка A1 с кэшем на 90 секунд и `generatedAt`, «Обновить».
- Выгрузки Excel: сводка, продавцы, мастера, запросы, заявки. Миграций нет.

### Фаза 7 — рассылки, пользователи, журнал, сотрудники

- Миграция V21: `broadcasts`, `broadcast_recipients`.
- Новое: рассылки с фоновой отправкой; раздел «Пользователи»; просмотр журнала; сотрудники и права ролей.
- Приложение:
  - пуш `BROADCAST {broadcastId}`;
  - `POST /api/v1/broadcasts/{id}/opened`.
- В задаче было `POST /notifications/{id}/opened`. Списка уведомлений в приложении нет, поэтому отметка «открыто»
  сделана для рассылки.

### После интеграции фронта

- `pending.latest[].title` у жалоб — готовый текст вместо кодов.
- `POST /admin/market/map/publish` принимает необязательный `{comment}` для журнала.
- `POST /admin/users/{id}/message` отвечает `{recipients}` вместо 204.
- Блокировка активного сотрудника админки — 409 `STAFF_BLOCK`.
- Лимит входа считает только неверные пароли.
- Документировано: пустая строка стирает `generation` модели; улицы без подписи (`name = null`) допустимы.

## Готовность

Все экраны A1–A9 и разделы сайдбара («Пользователи», «Журнал», «Сотрудники») собираются из ответов API без
вычислений на клиенте. Каждое изменяющее действие идёт через `@Audited`, и тест `AdminEndpointsTest` не даст
это нарушить. Права проверяются только на бэке. Интеграционные тесты `*IT` (Testcontainers) запускаются с Docker:
`mvn test`.
