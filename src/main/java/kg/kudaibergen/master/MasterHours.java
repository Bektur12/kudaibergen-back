package kg.kudaibergen.master;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.shop.ShopProperties;
import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.WeekDays;
import org.springframework.stereotype.Component;

/**
 * Принимает ли мастер прямо сейчас (39): действует, «Принимаю» включено, рабочий день и часы.
 * Только такие получают заявки. app.shops.ignore-working-hours действует и здесь — для проверки ночью.
 */
@Component
public class MasterHours {

   private final Clock clock;
   private final boolean ignoreWorkingHours;

   public MasterHours(Clock clock, ShopProperties properties) {
      this.clock = clock;
      this.ignoreWorkingHours = properties.ignoreWorkingHours();
   }

   public boolean openNow(Master master) {
      return master.isActive() && master.isAccepting() && withinSchedule(master, ZonedDateTime.now(clock));
   }

   public OpenStateDto state(Master master) {
      ZonedDateTime now = ZonedDateTime.now(clock);
      boolean open = openNow(master);
      return new OpenStateDto(open, !master.isAccepting(), master.getOpenFrom(), master.getOpenTo(),
            WeekDays.fromMask(master.getWorkDays()), open ? null : nextOpening(master, now));
   }

   private boolean withinSchedule(Master master, ZonedDateTime now) {
      if (ignoreWorkingHours) {
         return true;
      }
      return WeekDays.contains(master.getWorkDays(), now.getDayOfWeek())
            && !now.toLocalTime().isBefore(master.getOpenFrom()) && now.toLocalTime().isBefore(master.getOpenTo());
   }

   private static OffsetDateTime nextOpening(Master master, ZonedDateTime now) {
      for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
         ZonedDateTime day = now.plusDays(dayOffset);
         ZonedDateTime opening = day.with(master.getOpenFrom()).withSecond(0).withNano(0);
         if (WeekDays.contains(master.getWorkDays(), day.getDayOfWeek()) && opening.isAfter(now)) {
            return opening.toOffsetDateTime();
         }
      }
      return null;
   }
}
