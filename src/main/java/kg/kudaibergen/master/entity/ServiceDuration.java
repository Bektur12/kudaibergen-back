package kg.kudaibergen.master.entity;

import java.time.Duration;
import java.time.Instant;

import kg.kudaibergen.request.entity.RequestDuration;

/**
 * Сколько ждать отклики. По умолчанию — из справочника услуги: 30 минут, эвакуатор — 15 («Срочно»).
 * До конца дня — как у запросов на запчасти: до 17:00 по Бишкеку, если уже позже — 3 часа.
 */
public enum ServiceDuration {
   MIN_15(Duration.ofMinutes(15)),
   MIN_30(Duration.ofMinutes(30)),
   HOUR_1(Duration.ofHours(1)),
   HOUR_3(Duration.ofHours(3)),
   END_OF_DAY(null);

   private final Duration length;

   ServiceDuration(Duration length) {
      this.length = length;
   }

   public Instant expiresAt(Instant from) {
      return length == null ? RequestDuration.END_OF_DAY.expiresAt(from) : from.plus(length);
   }

   /** Длительность по умолчанию для услуги (service_types.duration_min). */
   public static ServiceDuration ofMinutes(int minutes) {
      return minutes <= 15 ? MIN_15 : minutes <= 30 ? MIN_30 : minutes <= 60 ? HOUR_1 : HOUR_3;
   }
}
