package kg.kudaibergen.auth;

import java.time.Duration;

import io.jsonwebtoken.JwtException;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.entity.AdminRole;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

   private final JwtService jwt = service("test-secret-test-secret-test-secret-32");

   @Test
   void accessТокенРазбираетсяОбратно() {
      User user = user(42L, true);

      AuthPrincipal principal = jwt.parseAccessToken(jwt.generateAccessToken(user));

      assertThat(principal.userId()).isEqualTo(42L);
      assertThat(principal.phone()).isEqualTo("+996555123456");
      assertThat(principal.adminRole()).isEqualTo(AdminRole.SUPERADMIN);
      assertThat(principal.isMarketAdmin()).isTrue();
      assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
   }

   @Test
   void токенСЧужойПодписьюОтклоняется() {
      String foreign = service("another-secret-another-secret-another-32").generateAccessToken(user(1L, false));

      assertThatThrownBy(() -> jwt.parseAccessToken(foreign)).isInstanceOf(JwtException.class);
   }

   private static JwtService service(String secret) {
      return new JwtService(new AppProperties(
            new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(30)), null, null, null, null));
   }

   private static User user(Long id, boolean admin) {
      User user = new User("+996555123456", Lang.RU);
      ReflectionTestUtils.setField(user, "id", id);
      ReflectionTestUtils.setField(user, "adminRole", admin ? AdminRole.SUPERADMIN : null);
      return user;
   }
}
