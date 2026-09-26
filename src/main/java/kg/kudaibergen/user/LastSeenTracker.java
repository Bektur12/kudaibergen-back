package kg.kudaibergen.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * lastSeenAt раньше обновлялся на WebSocket-дисконнект, но Centrifugo не проксирует события
 * отключения на бэкенд (сознательное ограничение OSS: https://centrifugal.dev/docs/server/proxy —
 * disconnect не поддерживается). Поэтому теперь это "последняя активность по REST" — обновляется
 * на каждый аутентифицированный запрос (см. JwtAuthFilter), с троттлингом, чтобы не писать в БД
 * на каждый чих. Точность отличается от точного момента дисконнекта на несколько минут — для
 * экрана "был(а) в сети недавно" этого достаточно.
 */
@Component
public class LastSeenTracker {

   private static final Duration THROTTLE = Duration.ofMinutes(1);

   private final UserService userService;
   private final Map<Long, Instant> lastTouched = new ConcurrentHashMap<>();

   public LastSeenTracker(UserService userService) {
      this.userService = userService;
   }

   @Async("appTaskExecutor")
   public void touch(Long userId) {
      Instant now = Instant.now();
      Instant previous = lastTouched.get(userId);
      if (previous != null && Duration.between(previous, now).compareTo(THROTTLE) < 0) {
         return;
      }
      lastTouched.put(userId, now);
      userService.touchLastSeen(userId, now);
   }
}
