package kg.kudaibergen.shop.entity;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Рабочие дни битовой маской: Пн = 1, Вт = 2, … Вс = 64. */
public final class WeekDays {

   public static final short ALL = 127;

   private WeekDays() {
   }

   public static short toMask(Set<DayOfWeek> days) {
      int mask = 0;
      for (DayOfWeek day : days) {
         mask |= bit(day);
      }
      return (short) mask;
   }

   public static Set<DayOfWeek> fromMask(short mask) {
      Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
      for (DayOfWeek day : DayOfWeek.values()) {
         if ((mask & bit(day)) != 0) {
            days.add(day);
         }
      }
      return days;
   }

   public static boolean contains(short mask, DayOfWeek day) {
      return (mask & bit(day)) != 0;
   }

   private static int bit(DayOfWeek day) {
      return 1 << (day.getValue() - 1);
   }
}
