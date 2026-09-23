package kg.kudaibergen.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Доступ к текущему пользователю там, где нет аргумента контроллера (например, в аспекте). */
public final class CurrentUser {

   private CurrentUser() {
   }

   public static AuthPrincipal principalOrNull() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
         return null;
      }
      return principal;
   }

   public static Long idOrNull() {
      AuthPrincipal principal = principalOrNull();
      return principal == null ? null : principal.userId();
   }
}
