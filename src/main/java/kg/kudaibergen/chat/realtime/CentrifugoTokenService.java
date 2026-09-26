package kg.kudaibergen.chat.realtime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.common.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Connection-токен для подключения клиента к Centrifugo (см. Client JWT в
 * https://centrifugal.dev/docs/server/authentication) — {@code sub} = наш userId, Centrifugo сам
 * его проверит своим HMAC-секретом (app.centrifugo.token-secret — отдельный от app.jwt.secret,
 * это токен для другого сервиса с другим временем жизни).
 */
@Service
public class CentrifugoTokenService {

   private final SecretKey key;
   private final long ttlSeconds;

   public CentrifugoTokenService(AppProperties properties) {
      AppProperties.Centrifugo config = properties.centrifugo();
      this.key = Keys.hmacShaKeyFor(config.tokenSecret().getBytes(StandardCharsets.UTF_8));
      this.ttlSeconds = config.tokenTtl().toSeconds();
   }

   public ConnectionToken issue(Long userId) {
      Instant now = Instant.now();
      String token = Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact();
      return new ConnectionToken(token, ttlSeconds);
   }

   public record ConnectionToken(String token, long expiresInSeconds) {
   }
}
