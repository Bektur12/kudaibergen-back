package kg.kudaibergen.common.security;

import kg.kudaibergen.user.entity.AdminRole;

/**
 * То, что лежит в SecurityContext после разбора JWT. Режим покупатель/продавец в токен
 * не кладём: он меняется на лету (PUT /me/role), а права продавца проверяются по магазину.
 * adminRole — null у обычных пользователей.
 */
public record AuthPrincipal(Long userId, String phone, AdminRole adminRole) {

   public boolean isSuperadmin() {
      return adminRole == AdminRole.SUPERADMIN;
   }

   /** Админ рынка или суперадмин. */
   public boolean isMarketAdmin() {
      return adminRole != null;
   }
}
