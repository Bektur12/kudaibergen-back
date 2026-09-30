package kg.kudaibergen.auth;

import java.time.Duration;
import java.util.Set;

import io.jsonwebtoken.JwtException;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

   private final JwtService jwt = service("test-secret-test-secret-test-secret-32");

   @Test
   void accessТокенПриложенияРазбираетсяОбратноБезПравАдминки() {
      AuthPrincipal principal = jwt.parseAccessToken(jwt.generateAccessToken(user(42L)));

      assertThat(principal.userId()).isEqualTo(42L);
      assertThat(principal.phone()).isEqualTo("+996555123456");
      assertThat(principal.isAdmin()).isFalse();
      assertThat(principal.permissions()).isEmpty();
      assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
   }

   @Test
   void токенАдминкиНесётРольИПрава() {
      String token = jwt.generateAdminToken(7L, "+996555000009", AdminRole.MARKET_ADMIN,
            Set.of(AdminPermission.SELLERS_VIEW, AdminPermission.PII_VIEW), Duration.ofMinutes(15));

      AuthPrincipal principal = jwt.parseAccessToken(token);

      assertThat(principal.isAdmin()).isTrue();
      assertThat(principal.adminRole()).isEqualTo(AdminRole.MARKET_ADMIN);
      assertThat(principal.can(AdminPermission.PII_VIEW)).isTrue();
      assertThat(principal.can(AdminPermission.STAFF_MANAGE)).isFalse();
   }

   @Test
   void токенСЧужойПодписьюОтклоняется() {
      String foreign = service("another-secret-another-secret-another-32").generateAccessToken(user(1L));

      assertThatThrownBy(() -> jwt.parseAccessToken(foreign)).isInstanceOf(JwtException.class);
   }

   private static JwtService service(String secret) {
      return new JwtService(new AppProperties(
            new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(30)), null, null, null, null, null, null, null));
   }

   private static User user(Long id) {
      User user = new User("+996555123456", Lang.RU);
      ReflectionTestUtils.setField(user, "id", id);
      return user;
   }
}
