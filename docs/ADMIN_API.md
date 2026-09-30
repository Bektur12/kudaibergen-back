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
  (10 попыток на номер за 15 минут, 30 на IP), а также `OTP_COOLDOWN` (новый код — через 42 с) и
  `OTP_BLOCKED` (5 неверных кодов — 15 минут).
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
`complaintsNew` — жалобы без решения. `null` — у сотрудника нет права на этот раздел. Бейдж «споры» добавится
в фазе 2.

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
- Просмотр журнала (`GET /admin/audit`) — фаза 7.

Коды действий фазы 1:

| action | entity | когда |
| --- | --- | --- |
| `SHOP_APPROVE`, `SHOP_REJECT`, `SHOP_BLOCK`, `SHOP_UNBLOCK` | SHOP | проверка и блокировка магазина (комментарий — причина) |
| `COMPLAINT_RESOLVE` | COMPLAINT | закрыть жалобу |
| `PART_REMOVE` | PART | удалить запчасть |
| `ROW_CONTAINERS_SET`, `CONTAINER_UPDATE` | ROW, CONTAINER | места в ряду, арендатор контейнера |
| `GEO_ANCHORS_SET`, `MAP_PUBLISH` | MAP | GPS-точки, новая схема |
| `ADMIN_LOGIN`, `ADMIN_PASSWORD_SET` | ADMIN | вход, пароль |

## Эндпоинты, которые уже были, — теперь по правам

| Эндпоинт | Право |
| --- | --- |
| `GET /admin/shops?status=`, `GET /admin/shops/verification-queue` | `SELLERS_VIEW` |
| `POST /admin/shops/{id}/approve`, `/reject {reason}` | `SELLERS_VERIFY` |
| `POST /admin/shops/{id}/block {reason}`, `/unblock` | `SELLERS_BLOCK` |
| `GET /admin/complaints` | `COMPLAINTS_VIEW` |
| `POST /admin/complaints/{id}/resolve {status, resolution}` | `COMPLAINTS_RESOLVE` |
| `DELETE /admin/parts/{id}` | `CONTENT_REMOVE` |
| `PUT /admin/market/rows/{id}/containers`, `PATCH /admin/market/containers/{id}` | `MARKET_EDIT` |
| `GET /admin/market/qr/{token}.png`, `GET /admin/market/qr/sheet?rowId=` | `MARKET_VIEW` |
| `PUT /admin/market/geo-anchors`, `PUT /admin/market/map` | `MARKET_MAP_PUBLISH` |

Эти эндпоинты в фазах 2–4 заменятся полноценными экранами A2 и A3 (списки с `counts`, карточки, черновик карты).

## Первый суперадмин

При старте, если активного `SUPER_ADMIN` нет и задан `ADMIN_BOOTSTRAP_PHONE`, этот номер становится суперадмином.
Пользователь создаётся, если его нет; имя берётся из `ADMIN_BOOTSTRAP_NAME`. Пароль суперадмин задаёт сам:
`POST /admin/auth/password/code`, затем `POST /admin/auth/password`. Остальных сотрудников заводит суперадмин
(`/admin/staff`, фаза 7). Пока экрана сотрудников нет — вставкой в `admin_members`.

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
