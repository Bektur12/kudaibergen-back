package kg.kudaibergen.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kg.kudaibergen.common.error.RateLimitException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Фиксированное окно в Redis: INCR + EXPIRE одним Lua-скриптом, чтобы счётчик
 * не остался без TTL при падении между командами. Общий для OTP и создания запросов.
 */
@Component
public class RateLimiter {

   private static final String PREFIX = "rl:";

   /** Возвращает {счётчик, оставшийся TTL в мс}. */
   private static final RedisScript<List> HIT = RedisScript.of("""
         local count = redis.call('INCR', KEYS[1])
         if count == 1 then
           redis.call('PEXPIRE', KEYS[1], ARGV[1])
         end
         return {count, redis.call('PTTL', KEYS[1])}
         """, List.class);

   private final StringRedisTemplate redis;

   public RateLimiter(StringRedisTemplate redis) {
      this.redis = redis;
   }

   /**
    * Засчитывает одно обращение по ключу. Если лимит в окне превышен —
    * бросает 429 с кодом {@code code} и retryAfter до конца окна.
    */
   public void hit(String key, int limit, Duration window, String code, String message) {
      List<?> result = redis.execute(HIT, List.of(PREFIX + key), String.valueOf(window.toMillis()));
      long count = ((Number) result.get(0)).longValue();
      if (count > limit) {
         long ttlMillis = Math.max(((Number) result.get(1)).longValue(), 1000);
         throw new RateLimitException(code, message, (ttlMillis + 999) / 1000);
      }
   }

   /**
    * Проверка без счёта: лимит в окне уже исчерпан — 429. Вместе с {@link #hit} на неудаче даёт лимит
    * только неудачных попыток: удачные не тратят его, а после исчерпания не проходит и верная попытка.
    */
   public void check(String key, int limit, String code, String message) {
      String value = redis.opsForValue().get(PREFIX + key);
      if (value != null && Long.parseLong(value) >= limit) {
         Long ttlMillis = redis.getExpire(PREFIX + key, TimeUnit.MILLISECONDS);
         throw new RateLimitException(code, message, (Math.max(ttlMillis == null ? 0 : ttlMillis, 1000) + 999) / 1000);
      }
   }
}
