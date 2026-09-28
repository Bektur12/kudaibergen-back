package kg.kudaibergen.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * «Был в сети»: время последнего аутентифицированного запроса (см. JwtAuthFilter).
 * Пишем в БД не чаще раза в минуту на пользователя.
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
