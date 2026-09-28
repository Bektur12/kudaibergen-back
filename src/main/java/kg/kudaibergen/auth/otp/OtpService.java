package kg.kudaibergen.auth.otp;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * SMS-коды в Redis. Код из 4 цифр живёт {@code codeTtl} (2 мин), новый можно запросить
 * через {@code resendInterval} (42 с). После {@code maxAttempts} (5) неверных вводов номер
 * блокируется на {@code blockDuration} (15 мин) — и для входа, и для остальных кодов.
 * В Redis лежит только HMAC кода, не сам код.
 *
 * <pre>
 * otp:{purpose}:{phone}          hash {h, attempts}   TTL codeTtl
 * otp:cooldown:{purpose}:{phone} "1"                  TTL resendInterval
 * otp:block:{phone}              "1"                  TTL blockDuration
 * rl:otp:phone:{phone}, rl:otp:ip:{ip}                счётчики RateLimiter
 * </pre>
 */
@Service
public class OtpService {

   private static final SecureRandom RANDOM = new SecureRandom();
   private static final String FIELD_HASH = "h";
   private static final String FIELD_ATTEMPTS = "attempts";

   private final StringRedisTemplate redis;
   private final RateLimiter rateLimiter;
   private final AppProperties.Otp config;
   private final SecretKeySpec hmacKey;

   public OtpService(StringRedisTemplate redis, RateLimiter rateLimiter, AppProperties properties) {
      this.redis = redis;
      this.rateLimiter = rateLimiter;
      this.config = properties.otp();
      this.hmacKey = new SecretKeySpec(config.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
   }

   /** Выпускает новый код. Бросает 429, если номер заблокирован, не прошла пауза или исчерпан лимит. */
   public String issue(OtpPurpose purpose, String phone, String clientIp) {
      ensureNotBlocked(phone);
      String cooldownKey = cooldownKey(purpose, phone);
      Boolean cooldownSet = redis.opsForValue().setIfAbsent(cooldownKey, "1", config.resendInterval());
      if (!Boolean.TRUE.equals(cooldownSet)) {
         long retryAfter = ttlSeconds(cooldownKey, config.resendInterval());
         throw new RateLimitException("OTP_COOLDOWN", "Новый код можно запросить через " + retryAfter + " с",
               retryAfter);
      }
      rateLimiter.hit("otp:phone:" + phone, config.phoneLimit(), config.limitWindow(),
            "OTP_RATE_LIMITED", "Слишком много кодов на этот номер, попробуйте позже");
      if (clientIp != null) {
         rateLimiter.hit("otp:ip:" + clientIp, config.ipLimit(), config.limitWindow(),
               "OTP_RATE_LIMITED", "Слишком много запросов кода, попробуйте позже");
      }

      String code = config.hasFixedCode() ? config.fixedCode() : String.format("%04d", RANDOM.nextInt(10_000));
      String key = codeKey(purpose, phone);
      redis.opsForHash().putAll(key, Map.of(FIELD_HASH, hash(purpose, phone, code), FIELD_ATTEMPTS, "0"));
      redis.expire(key, config.codeTtl());
      return code;
   }

   /**
    * Проверяет код и в случае успеха гасит его (DEL — ровно один выигравший при параллельных вызовах).
    * Неверный код увеличивает счётчик попыток; на последней попытке код удаляется, номер блокируется.
    */
   public void verify(OtpPurpose purpose, String phone, String code) {
      ensureNotBlocked(phone);
      String key = codeKey(purpose, phone);
      Object stored = redis.opsForHash().get(key, FIELD_HASH);
      if (stored == null) {
         throw expired();
      }
      if (MessageDigest.isEqual(stored.toString().getBytes(StandardCharsets.UTF_8),
            hash(purpose, phone, code).getBytes(StandardCharsets.UTF_8))) {
         if (!Boolean.TRUE.equals(redis.delete(key))) {
            throw expired();
         }
         return;
      }

      Long attempts = redis.opsForHash().increment(key, FIELD_ATTEMPTS, 1);
      if (Long.valueOf(-1).equals(redis.getExpire(key))) {
         // код истёк между чтением и инкрементом: HINCRBY создал ключ без TTL — убираем его
         redis.delete(key);
         throw expired();
      }
      long left = config.maxAttempts() - attempts;
      if (left <= 0) {
         redis.delete(key);
         redis.opsForValue().set(blockKey(phone), "1", config.blockDuration());
         throw blocked(config.blockDuration().toSeconds());
      }
      throw (BadRequestException) new BadRequestException("OTP_INVALID", "Неверный код")
            .with("attemptsLeft", left);
   }

   public Duration codeTtl() {
      return config.codeTtl();
   }

   public Duration resendInterval() {
      return config.resendInterval();
   }

   private void ensureNotBlocked(String phone) {
      if (Boolean.TRUE.equals(redis.hasKey(blockKey(phone)))) {
         throw blocked(ttlSeconds(blockKey(phone), config.blockDuration()));
      }
   }

   private long ttlSeconds(String key, Duration fallback) {
      Long ttl = redis.getExpire(key);
      return ttl == null || ttl <= 0 ? fallback.toSeconds() : ttl;
   }

   private static RateLimitException blocked(long retryAfter) {
      return new RateLimitException("OTP_BLOCKED",
            "Слишком много неверных кодов. Попробуйте через " + ((retryAfter + 59) / 60) + " мин", retryAfter);
   }

   private static BadRequestException expired() {
      return new BadRequestException("OTP_EXPIRED", "Код истёк или не запрашивался, запросите новый");
   }

   private String hash(OtpPurpose purpose, String phone, String code) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(hmacKey);
         byte[] digest = mac.doFinal((purpose.key() + ":" + phone + ":" + code).getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(digest);
      } catch (NoSuchAlgorithmException | InvalidKeyException e) {
         throw new IllegalStateException("HmacSHA256 недоступен", e);
      }
   }

   private static String codeKey(OtpPurpose purpose, String phone) {
      return "otp:" + purpose.key() + ":" + phone;
   }

   private static String cooldownKey(OtpPurpose purpose, String phone) {
      return "otp:cooldown:" + purpose.key() + ":" + phone;
   }

   private static String blockKey(String phone) {
      return "otp:block:" + phone;
   }
}
