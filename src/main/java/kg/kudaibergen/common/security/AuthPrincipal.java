package kg.kudaibergen.common.security;

import java.util.Set;

import org.springframework.lang.Nullable;

/**
 * То, что лежит в SecurityContext после разбора JWT. Режим покупатель/продавец в токен
 * не кладём: он меняется на лету (PUT /me/role), а права продавца проверяются по магазину.
 * adminRole и permissions заполнены только в сессии админки (токен с audience admin);
 * такой токен принимается только на /api/v1/admin/**.
 */
public record AuthPrincipal(Long userId, String phone, @Nullable AdminRole adminRole,
                            Set<AdminPermission> permissions) {

   public AuthPrincipal {
      permissions = Set.copyOf(permissions);
   }

   /** Сессия мобильного приложения. */
   public static AuthPrincipal user(Long userId, String phone) {
      return new AuthPrincipal(userId, phone, null, Set.of());
   }

   public boolean isAdmin() {
      return adminRole != null;
   }

   public boolean can(AdminPermission permission) {
      return permissions.contains(permission);
   }
}
