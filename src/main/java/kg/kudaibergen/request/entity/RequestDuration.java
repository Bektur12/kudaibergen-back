package kg.kudaibergen.request.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import kg.kudaibergen.common.config.ClockConfig;

/** «Сколько ждать ответы» (06б). До конца дня — до 17:00 по Бишкеку; если уже позже — 3 часа. */
public enum RequestDuration {
   MIN_30(Duration.ofMinutes(30)),
   HOUR_1(Duration.ofHours(1)),
   HOUR_3(Duration.ofHours(3)),
   END_OF_DAY(null);

   static final LocalTime MARKET_CLOSES = LocalTime.of(17, 0);
   static final Duration AFTER_CLOSING = Duration.ofHours(3);

   private final Duration length;

   RequestDuration(Duration length) {
      this.length = length;
   }

   /** Когда истечёт запрос, отправленный в {@code from}. */
   public Instant expiresAt(Instant from) {
      if (length != null) {
         return from.plus(length);
      }
      ZonedDateTime now = from.atZone(ClockConfig.MARKET_ZONE);
      ZonedDateTime closes = now.with(MARKET_CLOSES);
      return now.isBefore(closes) ? closes.toInstant() : from.plus(AFTER_CLOSING);
   }
}
