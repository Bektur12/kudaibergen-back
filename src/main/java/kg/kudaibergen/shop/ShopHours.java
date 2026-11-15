package kg.kudaibergen.shop;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.WeekDays;
import org.springframework.stereotype.Component;

/**
 * Открыт ли бокс сейчас — по его часам и дням работы, время Бишкека. Рынки по понедельникам не работают
 * (по умолчанию вт–вс 9:00–17:00), поэтому «● Открыто до 17:00» в понедельник или вечером вводило покупателей в
 * заблуждение. Запросы это не останавливает: продавец ответит, когда откроется.
 */
@Component
public class ShopHours {

   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

   private final Clock clock;

   public ShopHours(Clock clock) {
      this.clock = clock;
   }

   public boolean openNow(Shop shop) {
      return shop.isActive() && openAt(shop.getOpenFrom(), shop.getOpenTo(), shop.getWorkDays(), now());
   }

   public OpenStateDto state(Shop shop) {
      ZonedDateTime now = now();
      boolean open = shop.isActive() && openAt(shop.getOpenFrom(), shop.getOpenTo(), shop.getWorkDays(), now);
      OffsetDateTime opensAt = open || !shop.isActive() ? null
            : nextOpening(shop.getOpenFrom(), shop.getOpenTo(), shop.getWorkDays(), now);
      return new OpenStateDto(open, false, shop.getOpenFrom(), shop.getOpenTo(), shop.workDaySet(), opensAt);
   }

   private ZonedDateTime now() {
      return ZonedDateTime.now(clock.withZone(BISHKEK));
   }

   /** Рабочий день и время в [from, to). to раньше from — смена через полночь (до to следующего дня). */
   static boolean openAt(LocalTime from, LocalTime to, short days, ZonedDateTime at) {
      LocalTime time = at.toLocalTime();
      DayOfWeek today = at.getDayOfWeek();
      if (from.equals(to)) {
         return WeekDays.contains(days, today);
      }
      if (from.isBefore(to)) {
         return WeekDays.contains(days, today) && !time.isBefore(from) && time.isBefore(to);
      }
      return (WeekDays.contains(days, today) && !time.isBefore(from))
            || (WeekDays.contains(days, today.minus(1)) && time.isBefore(to));
   }

   /** «Откроется в 09:00» / «во вторник в 09:00»: ближайшее начало смены; дней работы нет — null. */
   static OffsetDateTime nextOpening(LocalTime from, LocalTime to, short days, ZonedDateTime now) {
      LocalDate date = now.toLocalDate();
      for (int i = 0; i <= 7; i++) {
         LocalDate day = date.plusDays(i);
         if (!WeekDays.contains(days, day.getDayOfWeek())) {
            continue;
         }
         ZonedDateTime start = day.atTime(from).atZone(BISHKEK);
         if (start.isAfter(now)) {
            return start.toOffsetDateTime();
         }
      }
      return null;
   }
}
