package kg.kudaibergen.common.security;

/**
 * Права админки. Каждый эндпоинт /api/v1/admin/** закрыт {@code @PreAuthorize("hasAuthority('…')")}
 * по одному из них. Новое право — строка здесь; SUPER_ADMIN получает его сразу, другим ролям — миграцией.
 */
public enum AdminPermission {
   DASHBOARD_VIEW, EXPORT_EXCEL,
   SELLERS_VIEW, SELLERS_VERIFY, SELLERS_BLOCK, DISPUTES_RESOLVE,
   MASTERS_VIEW, MASTERS_VERIFY, MASTERS_BLOCK, MASTERS_CREATE,
   MARKET_VIEW, MARKET_EDIT, MARKET_MAP_PUBLISH, TENANTS_IMPORT,
   /** REQUESTS_MANAGE — скрыть заявку, расширить радиус. */
   REQUESTS_VIEW, REQUESTS_MANAGE,
   COMPLAINTS_VIEW, COMPLAINTS_RESOLVE, CONTENT_REMOVE, SELLER_WARN,
   DICTIONARIES_VIEW, DICTIONARIES_EDIT,
   BROADCASTS_VIEW, BROADCASTS_SEND,
   /** Без PII_VIEW телефоны в ответах админки маскируются: +996 *** ** 34 56. */
   USERS_VIEW, USERS_BLOCK, PII_VIEW,
   /** Кнопка «Написать»: пуш пользователю от администрации. */
   MESSAGE_USERS,
   AUDIT_VIEW, STAFF_MANAGE
}
