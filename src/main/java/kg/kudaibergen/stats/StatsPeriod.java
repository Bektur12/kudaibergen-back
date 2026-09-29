package kg.kudaibergen.stats;

import java.time.Duration;

/** «Неделя / Месяц» на экране 17: последние 7 или 30 дней от текущего момента. */
public enum StatsPeriod {
   WEEK(7),
   MONTH(30);

   private final int days;

   StatsPeriod(int days) {
      this.days = days;
   }

   public int days() {
      return days;
   }

   public Duration length() {
      return Duration.ofDays(days);
   }
}
