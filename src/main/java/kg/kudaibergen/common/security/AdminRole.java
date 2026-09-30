package kg.kudaibergen.common.security;

/**
 * Роль сотрудника админки. В коде роли не сравниваются — только права (AdminPermission):
 * SUPER_ADMIN имеет все права всегда, остальные — по таблице admin_role_permissions.
 */
public enum AdminRole {
   /** Владелец / техадмин. */
   SUPER_ADMIN("Суперадмин"),
   /** «Администратор рынка»: всё, кроме управления сотрудниками. */
   MARKET_ADMIN("Администратор рынка");

   private final String defaultTitle;

   AdminRole(String defaultTitle) {
      this.defaultTitle = defaultTitle;
   }

   /** Подпись в сайдбаре, если при создании сотрудника её не указали. */
   public String defaultTitle() {
      return defaultTitle;
   }

   public boolean hasAllPermissions() {
      return this == SUPER_ADMIN;
   }
}
