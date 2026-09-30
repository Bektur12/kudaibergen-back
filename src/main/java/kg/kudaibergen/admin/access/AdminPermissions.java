package kg.kudaibergen.admin.access;

import java.util.EnumSet;
import java.util.Set;

import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Матрица прав. SUPER_ADMIN — все права всегда (в том числе новые, и не может запереть сам себя);
 * остальные роли — по таблице admin_role_permissions, которую суперадмин может донастроить.
 * {@link #defaults} — то, что заложено миграцией, для справки и тестов.
 */
@Component
public class AdminPermissions {

   private final JdbcTemplate jdbc;

   public AdminPermissions(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public Set<AdminPermission> of(AdminRole role) {
      if (role.hasAllPermissions()) {
         return EnumSet.allOf(AdminPermission.class);
      }
      Set<AdminPermission> result = EnumSet.noneOf(AdminPermission.class);
      for (String name : jdbc.queryForList(
            "select permission from admin_role_permissions where admin_role = ?", String.class, role.name())) {
         try {
            result.add(AdminPermission.valueOf(name));
         } catch (IllegalArgumentException retired) {
            // право убрали из кода, строка в таблице осталась — игнорируем
         }
      }
      return result;
   }

   public static Set<AdminPermission> defaults(AdminRole role) {
      return switch (role) {
         case SUPER_ADMIN -> EnumSet.allOf(AdminPermission.class);
         case MARKET_ADMIN -> EnumSet.complementOf(EnumSet.of(AdminPermission.STAFF_MANAGE));
      };
   }
}
