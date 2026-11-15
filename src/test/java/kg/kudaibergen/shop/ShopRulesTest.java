package kg.kudaibergen.shop;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.WeekDays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopRulesTest {

   /** Вторник 18.11.2026, 11:00 по Бишкеку (UTC+6). */
   private final ShopHours hours = at("2026-11-17T05:00:00Z");

   private static ShopHours at(String instant) {
      return new ShopHours(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
   }

   private static Shop activeShop() {
      Shop shop = new Shop(1L, "Автодеталь Азамат", 10L);
      shop.verified();
      return shop;
   }

   @Test
   void рынокПоУмолчаниюВтВс9до17() {
      Shop shop = activeShop();
      assertThat(shop.getOpenFrom()).isEqualTo(LocalTime.of(9, 0));
      assertThat(shop.getOpenTo()).isEqualTo(LocalTime.of(17, 0));
      assertThat(shop.workDaySet()).doesNotContain(DayOfWeek.MONDAY).hasSize(6);

      OpenStateDto state = hours.state(shop);
      assertThat(state.openNow()).isTrue();
      assertThat(state.closedManually()).isFalse();
      assertThat(state.opensAt()).isNull();
   }

   @Test
   void вПонедельникЗакрыто_откроетсяВоВторникВ9() {
      // понедельник 16.11.2026, 11:00 по Бишкеку
      OpenStateDto state = at("2026-11-16T05:00:00Z").state(activeShop());
      assertThat(state.openNow()).isFalse();
      assertThat(state.opensAt()).isEqualTo(OffsetDateTime.parse("2026-11-17T09:00:00+06:00"));
   }

   @Test
   void вечеромЗакрыто_утромДоОткрытияТоже() {
      // вторник 17:00 — уже закрыто, откроется в среду в 9
      OpenStateDto evening = at("2026-11-17T11:00:00Z").state(activeShop());
      assertThat(evening.openNow()).isFalse();
      assertThat(evening.opensAt()).isEqualTo(OffsetDateTime.parse("2026-11-18T09:00:00+06:00"));
      // вторник 8:59 — ещё закрыто, откроется сегодня
      OpenStateDto morning = at("2026-11-17T02:59:00Z").state(activeShop());
      assertThat(morning.openNow()).isFalse();
      assertThat(morning.opensAt()).isEqualTo(OffsetDateTime.parse("2026-11-17T09:00:00+06:00"));
   }

   @Test
   void магазинВнеРынкаРаботаетИВПонедельник() {
      Shop center = Shop.offMarket(1L, "Kia центр", "пр. Чуй 155", 42.87, 74.6);
      center.verified();
      assertThat(center.workDaySet()).contains(DayOfWeek.MONDAY);
      assertThat(at("2026-11-16T05:00:00Z").openNow(center)).isTrue();
   }

   @Test
   void непроверенныйМагазинНикогдаНеОткрыт() {
      Shop pending = new Shop(1L, "Korea Plus", 11L);
      assertThat(pending.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(hours.openNow(pending)).isFalse();
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
      assertThat(ShopNames.validate("А")).isEqualTo("А");
      assertThatThrownBy(() -> ShopNames.validate("  ")).isInstanceOf(BadRequestException.class);
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
