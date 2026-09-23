package kg.kudaibergen.common.security;

import kg.kudaibergen.user.entity.UserRole;

/** То, что лежит в SecurityContext после разбора JWT. */
public record AuthPrincipal(Long userId, String phone, UserRole role) {

   public boolean isSeller() {
      return role == UserRole.SELLER;
   }
}
