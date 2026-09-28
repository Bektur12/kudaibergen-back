package kg.kudaibergen.shop;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;

import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.WeekDays;
import org.springframework.stereotype.Component;

/**
 * Открыт ли бокс прямо сейчас: магазин действует, тумблер «Бокс закрыт» выключен,
 * сегодня рабочий день и время в интервале часов работы. Иначе — когда откроется
 * («Откроется в 08:00»). Только такие магазины получают запросы.
 */
@Component
public class ShopHours {

   private final Clock clock;

   public ShopHours(Clock clock) {
      this.clock = clock;
   }

   public boolean openNow(Shop shop) {
      return shop.isActive() && shop.isOpen() && withinSchedule(shop, ZonedDateTime.now(clock));
   }

   public OpenStateDto state(Shop shop) {
      ZonedDateTime now = ZonedDateTime.now(clock);
      boolean open = shop.isActive() && shop.isOpen() && withinSchedule(shop, now);
      Set<DayOfWeek> days = shop.workDaySet();
      return new OpenStateDto(open, !shop.isOpen(), shop.getOpenFrom(), shop.getOpenTo(), days,
            open ? null : nextOpening(shop, now));
   }

   private static boolean withinSchedule(Shop shop, ZonedDateTime now) {
      LocalTime time = now.toLocalTime();
      return WeekDays.contains(shop.getWorkDays(), now.getDayOfWeek())
            && !time.isBefore(shop.getOpenFrom()) && time.isBefore(shop.getOpenTo());
   }

   /**
    * Ближайшее начало рабочего времени после текущего момента: сегодня, если ещё не открылись,
    * иначе в следующий рабочий день. Закрыт тумблером посреди дня — тоже «откроется завтра в 08:00».
    */
   static OffsetDateTime nextOpening(Shop shop, ZonedDateTime now) {
      for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
         ZonedDateTime day = now.plusDays(dayOffset);
         ZonedDateTime opening = day.with(shop.getOpenFrom()).withSecond(0).withNano(0);
         if (WeekDays.contains(shop.getWorkDays(), day.getDayOfWeek()) && opening.isAfter(now)) {
            return opening.toOffsetDateTime();
         }
      }
      return null;
   }
}
