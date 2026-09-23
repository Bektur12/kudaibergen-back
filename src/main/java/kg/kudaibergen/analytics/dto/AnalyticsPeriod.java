package kg.kudaibergen.analytics.dto;

import java.time.Duration;

public enum AnalyticsPeriod {
   WEEK(Duration.ofDays(7)),
   MONTH(Duration.ofDays(30));

   private final Duration duration;

   AnalyticsPeriod(Duration duration) {
      this.duration = duration;
   }

   public Duration duration() {
      return duration;
   }
}
