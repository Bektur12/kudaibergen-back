package kg.kudaibergen.admin.access;

import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.security.CurrentUser;
import org.springframework.lang.Nullable;

/**
 * Телефоны в ответах админки: полный — с правом PII_VIEW, иначе {@code +996 *** ** 34 56}.
 * Решает сервер, фронт показывает как есть. В DTO — через {@link MaskedPhone}, в выгрузках — {@link #forViewer}.
 */
public final class Phones {

   private Phones() {
   }

   @Nullable
   public static String forViewer(@Nullable String phone) {
      if (phone == null) {
         return null;
      }
      AuthPrincipal viewer = CurrentUser.principalOrNull();
      return viewer != null && viewer.can(AdminPermission.PII_VIEW) ? phone : mask(phone);
   }

   public static String mask(String phone) {
      String digits = phone.replaceAll("\\D", "");
      if (digits.length() < 4) {
         return "***";
      }
      String tail = digits.substring(digits.length() - 4, digits.length() - 2) + " "
            + digits.substring(digits.length() - 2);
      return phone.startsWith("+996") ? "+996 *** ** " + tail : "*** " + tail;
   }
}
