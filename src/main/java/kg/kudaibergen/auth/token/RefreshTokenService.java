package kg.kudaibergen.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ротация refresh-токенов: каждый обмен гасит старый и выдаёт новый.
 * Предъявление уже погашенного токена = признак утечки: гасим все сессии пользователя этого вида
 * (приложение или админка). Токен другого вида считается неизвестным.
 */
@Service
public class RefreshTokenService {

   private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final int TOKEN_BYTES = 32;

   private final RefreshTokenRepository tokens;
   private final Duration ttl;

   public RefreshTokenService(RefreshTokenRepository tokens, AppProperties properties) {
      this.tokens = tokens;
      this.ttl = properties.jwt().refreshTtl();
   }

   @Transactional
   public String issue(Long userId) {
      return issue(userId, TokenAudience.APP, ttl);
   }

   @Transactional
   public String issue(Long userId, TokenAudience audience, Duration lifetime) {
      byte[] raw = new byte[TOKEN_BYTES];
      RANDOM.nextBytes(raw);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
      tokens.save(new RefreshToken(userId, sha256(token), Instant.now().plus(lifetime), audience));
      return token;
   }

   /** Гасит предъявленный токен приложения и возвращает id владельца. Новый токен выпускает вызывающий. */
   @Transactional(noRollbackFor = UnauthorizedException.class)
   public Long consume(String token) {
      return consume(token, TokenAudience.APP);
   }

   @Transactional(noRollbackFor = UnauthorizedException.class)
   public Long consume(String token, TokenAudience audience) {
      Instant now = Instant.now();
      RefreshToken stored = tokens.findByTokenHash(sha256(token))
            .filter(found -> found.getAudience() == audience)
            .orElseThrow(RefreshTokenService::invalid);
      if (stored.isExpired(now)) {
         throw invalid();
      }
      if (stored.isRevoked() || tokens.revoke(stored.getId(), now) == 0) {
         log.warn("Повторное использование refresh-токена ({}) пользователя {} — гасим эти сессии", audience,
               stored.getUserId());
         tokens.revokeAllOfUser(stored.getUserId(), audience, now);
         throw invalid();
      }
      return stored.getUserId();
   }

   /** Выход: гасим токен, только если он принадлежит этому пользователю. */
   @Transactional
   public void revoke(String token, Long userId) {
      tokens.findByTokenHash(sha256(token))
            .filter(stored -> stored.getUserId().equals(userId))
            .ifPresent(stored -> tokens.revoke(stored.getId(), Instant.now()));
   }

   /** Выход по одному refresh-токену (cookie админки): гасим, если он этого вида. */
   @Transactional
   public void revoke(String token, TokenAudience audience) {
      tokens.findByTokenHash(sha256(token))
            .filter(stored -> stored.getAudience() == audience)
            .ifPresent(stored -> tokens.revoke(stored.getId(), Instant.now()));
   }

   /** Все сессии пользователя — и приложения, и админки (удаление аккаунта, блокировка). */
   @Transactional
   public void revokeAll(Long userId) {
      tokens.revokeAllOfUser(userId, Instant.now());
   }

   @Transactional
   public void revokeAll(Long userId, TokenAudience audience) {
      tokens.revokeAllOfUser(userId, audience, Instant.now());
   }

   @Scheduled(cron = "0 30 3 * * *")
   @Transactional
   public void cleanup() {
      int removed = tokens.deleteExpiredBefore(Instant.now());
      if (removed > 0) {
         log.info("Удалено просроченных refresh-токенов: {}", removed);
      }
   }

   private static UnauthorizedException invalid() {
      return new UnauthorizedException("REFRESH_TOKEN_INVALID", "Сессия истекла, войдите заново");
   }

   static String sha256(String value) {
      try {
         return HexFormat.of().formatHex(
               MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException("SHA-256 недоступен", e);
      }
   }
}
