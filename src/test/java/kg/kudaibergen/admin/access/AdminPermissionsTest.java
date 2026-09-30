package kg.kudaibergen.admin.access;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionsTest {

   @Test
   void суперадминИмеетВсёАдминРынкаВсёКромеСотрудников() {
      assertThat(AdminPermissions.defaults(AdminRole.SUPER_ADMIN)).containsExactlyInAnyOrder(AdminPermission.values());
      assertThat(AdminPermissions.defaults(AdminRole.MARKET_ADMIN))
            .doesNotContain(AdminPermission.STAFF_MANAGE)
            .contains(AdminPermission.AUDIT_VIEW, AdminPermission.PII_VIEW)
            .hasSize(AdminPermission.values().length - 1);
   }

   @Test
   void миграцияЗакладываетТуЖеМатрицуЧтоИКод() throws Exception {
      String sql = Files.readString(Path.of("src/main/resources/db/migration/V14__admin_access.sql"));
      Matcher block = Pattern.compile("SELECT 'MARKET_ADMIN', p FROM unnest\\(ARRAY\\[(.*?)]\\)", Pattern.DOTALL)
            .matcher(sql);
      assertThat(block.find()).isTrue();
      Set<AdminPermission> seeded = EnumSet.noneOf(AdminPermission.class);
      Matcher names = Pattern.compile("'([A-Z_]+)'").matcher(block.group(1));
      while (names.find()) {
         seeded.add(AdminPermission.valueOf(names.group(1)));
      }
      assertThat(seeded).isEqualTo(AdminPermissions.defaults(AdminRole.MARKET_ADMIN));
   }
}
