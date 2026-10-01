package kg.kudaibergen.chat.realtime;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.common.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * JWT для клиента Centrifugo (https://centrifugal.dev/docs/server/authentication): токен подключения
 * ({@code sub} = userId) и токен подписки на канал чата ({@code sub} + {@code channel}). Оба подписаны
 * app.centrifugo.token-secret — Centrifugo проверяет их тем же ключом (client.token.hmac_secret_key).
 */
@Service
public class CentrifugoTokens {

   private final SecretKey key;
   private final long ttlSeconds;
   private final Clock clock;

   public CentrifugoTokens(AppProperties properties, Clock clock) {
      AppProperties.Centrifugo config = properties.centrifugo();
      this.key = Keys.hmacShaKeyFor(config.tokenSecret().getBytes(StandardCharsets.UTF_8));
      this.ttlSeconds = config.tokenTtl().toSeconds();
      this.clock = clock;
   }

   public Token connection(Long userId) {
      Instant now = clock.instant();
      return new Token(Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact(), ttlSeconds);
   }

   /** Право участника подписаться на канал чата. Проверка участия — до вызова, в ChatService. */
   public Token subscription(Long userId, String channel) {
      Instant now = clock.instant();
      return new Token(Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("channel", channel)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact(), ttlSeconds);
   }

   public record Token(String token, long expiresInSeconds) {
   }
}
