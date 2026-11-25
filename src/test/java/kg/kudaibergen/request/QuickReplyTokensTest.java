package kg.kudaibergen.request;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import kg.kudaibergen.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickReplyTokensTest {

   private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
   private static final Instant END = NOW.plus(Duration.ofMinutes(30));

   private static QuickReplyTokens tokens(String secret, Instant now) {
      return new QuickReplyTokens(new AppProperties(new AppProperties.Jwt(secret, Duration.ofMinutes(15),
            Duration.ofDays(30)), null, null, null, null, null, null, null), Clock.fixed(now, ZoneOffset.UTC));
   }

   @Test
   void подлинныйКлючДаётПродавцаЭтогоЗапроса() {
      QuickReplyTokens tokens = tokens("secret-secret-secret-secret-32b!", NOW);
      String token = tokens.issue(7L, 3L, 42L, END);

      assertThat(tokens.verify(token, 7L)).isEqualTo(42L);
   }

   @Test
   void чужойЗапросПодделкаИСрокНеПроходят() {
      QuickReplyTokens tokens = tokens("secret-secret-secret-secret-32b!", NOW);
      String token = tokens.issue(7L, 3L, 42L, END);

      assertThat(tokens.verify(token, 8L)).as("ключ от другого запроса").isNull();
      assertThat(tokens.verify(token.replace('.', 'x'), 7L)).as("испорченный").isNull();
      assertThat(tokens.verify(tokens("other-secret-other-secret-32b!!!", NOW).issue(7L, 3L, 42L, END), 7L))
            .as("подписан чужим ключом").isNull();
      assertThat(tokens("secret-secret-secret-secret-32b!", END.plusSeconds(1)).verify(token, 7L))
            .as("запрос закончился").isNull();
      assertThat(tokens.verify(null, 7L)).isNull();
      assertThat(tokens.verify("мусор", 7L)).isNull();
   }
}
