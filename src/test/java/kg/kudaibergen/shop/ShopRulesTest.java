package kg.kudaibergen.shop;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.WeekDays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopRulesTest {

   /** Вторник, 29.09.2026, Бишкек. */
   private static ShopHours at(int hour, int minute) {
      return at(2026, 9, 29, hour, minute);
   }

   private static ShopHours at(int year, int month, int day, int hour, int minute) {
      ZonedDateTime now = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ClockConfig.MARKET_ZONE);
      return new ShopHours(Clock.fixed(now.toInstant(), ClockConfig.MARKET_ZONE));
   }

   private static Shop activeShop() {
      Shop shop = new Shop(1L, "Автодеталь Азамат", 10L);
      shop.verified();
      return shop;
   }

   @Test
   void открытВРабочиеЧасыДо17() {
      OpenStateDto state = at(12, 0).state(activeShop());
      assertThat(state.openNow()).isTrue();
      assertThat(state.openTo()).isEqualTo(LocalTime.of(17, 0));
      assertThat(state.opensAt()).isNull();
   }

   @Test
   void вечеромЗакрытИОткроетсяЗавтраВ8() {
      OpenStateDto state = at(17, 0).state(activeShop());
      assertThat(state.openNow()).isFalse();
      assertThat(state.opensAt().toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 30, 8, 0));
   }

   @Test
   void утромДоОткрытияОткроетсяСегодня() {
      OpenStateDto state = at(7, 30).state(activeShop());
      assertThat(state.openNow()).isFalse();
      assertThat(state.opensAt().toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 29, 8, 0));
   }

   @Test
   void тумблерБоксЗакрытИВыходные() {
      Shop manual = activeShop();
      manual.setOpen(false);
      OpenStateDto closed = at(12, 0).state(manual);
      assertThat(closed.openNow()).isFalse();
      assertThat(closed.closedManually()).isTrue();

      Shop weekdaysOnly = activeShop();
      weekdaysOnly.setSchedule(null, null, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
      // суббота, 3 октября
      OpenStateDto saturday = at(2026, 10, 3, 12, 0).state(weekdaysOnly);
      assertThat(saturday.openNow()).isFalse();
      assertThat(saturday.opensAt().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
   }

   @Test
   void непроверенныйМагазинНикогдаНеОткрыт() {
      Shop pending = new Shop(1L, "Korea Plus", 11L);
      assertThat(pending.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(at(12, 0).openNow(pending)).isFalse();
   }

   @Test
   void проверкаИПереезд() {
      Shop shop = new Shop(1L, "Korea Plus", 11L);
      assertThat(shop.containerToVerify()).isEqualTo(11L);
      shop.verified();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
      assertThat(shop.containerToVerify()).isNull();

      shop.setPendingContainerId(22L);
      assertThat(shop.containerToVerify()).isEqualTo(22L);
      shop.verified();
      assertThat(shop.getContainerId()).isEqualTo(22L);
      assertThat(shop.getPendingContainerId()).isNull();

      shop.block("Жалобы покупателей");
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.BLOCKED);
      shop.unblock();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
      assertThat(shop.getBlockReason()).isNull();
   }

   @Test
   void названиеБезТелефоновИСсылок() {
      assertThat(ShopNames.validate("  Автодеталь   Азамат ")).isEqualTo("Автодеталь Азамат");
      assertThat(ShopNames.validate("Бокс 12")).isEqualTo("Бокс 12");
      assertThatThrownBy(() -> ShopNames.validate("А")).isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> ShopNames.validate("Запчасти 0555 123 456")).isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> ShopNames.validate("parts.kg")).isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> ShopNames.validate("Пишите @autoparts")).isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> ShopNames.validate("x".repeat(61))).isInstanceOf(BadRequestException.class);
   }

   @Test
   void рабочиеДниБитами() {
      Set<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
      short mask = WeekDays.toMask(days);
      assertThat(mask).isEqualTo((short) 65);
      assertThat(WeekDays.fromMask(mask)).isEqualTo(days);
      assertThat(WeekDays.fromMask(WeekDays.ALL)).hasSize(7);
   }

   @Test
   void маскаНомераАрендатора() {
      assertThat(ShopVerificationService.mask("+996555123411")).isEqualTo("+996 555 ••• •11");
   }
}
