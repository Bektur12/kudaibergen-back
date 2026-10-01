package kg.kudaibergen.notification.push;

import java.time.Clock;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

/** Тихие часы (ТЗ 11.3): с 22:00 до 07:00 по Бишкеку пуши приходят без звука. */
@Component
public class QuietHours {

   private static final LocalTime FROM = LocalTime.of(22, 0);
   private static final LocalTime TO = LocalTime.of(7, 0);

   private final Clock clock;

   public QuietHours(Clock clock) {
      this.clock = clock;
   }

   public boolean now() {
      LocalTime time = LocalTime.now(clock);
      return !time.isBefore(FROM) || time.isBefore(TO);
   }
}
