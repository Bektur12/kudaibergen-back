package kg.kudaibergen.admin.broadcasts;

import java.time.Instant;

import kg.kudaibergen.admin.broadcasts.BroadcastDtos.Audience;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastFilters;
import kg.kudaibergen.common.error.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BroadcastRulesTest {

   @Test
   void тихиеЧасыПереносятНаСемьУтра() {
      // 23:30 по Бишкеку (UTC+6) → 07:00 следующего дня
      assertThat(BroadcastService.outsideQuietHours(Instant.parse("2026-10-01T17:30:00Z")))
            .isEqualTo(Instant.parse("2026-10-02T01:00:00Z"));
      // 05:00 → 07:00 того же дня
      assertThat(BroadcastService.outsideQuietHours(Instant.parse("2026-10-01T23:00:00Z")))
            .isEqualTo(Instant.parse("2026-10-02T01:00:00Z"));
      // 12:00 — как есть
      Instant noon = Instant.parse("2026-10-01T06:00:00Z");
      assertThat(BroadcastService.outsideQuietHours(noon)).isEqualTo(noon);
      assertThat(BroadcastService.quiet(Instant.parse("2026-10-01T16:00:00Z"))).isTrue();
      assertThat(BroadcastService.quiet(Instant.parse("2026-10-01T15:59:00Z"))).isFalse();
   }

   @Test
   void подписьАудитории() {
      assertThat(BroadcastAudience.label(Audience.SELLERS, 64)).isEqualTo("Получат 64 продавца");
      assertThat(BroadcastAudience.label(Audience.SELLERS, 1)).isEqualTo("Получит 1 продавец");
      assertThat(BroadcastAudience.label(Audience.MASTERS, 11)).isEqualTo("Получат 11 мастеров");
      assertThat(BroadcastAudience.label(Audience.BUYERS, 22)).isEqualTo("Получат 22 покупателя");
   }

   @Test
   void фильтрыТолькоПодходящиеАудитории() {
      BroadcastAudience audience = new BroadcastAudience();
      assertThatThrownBy(() -> audience.of(Audience.MASTERS, new BroadcastFilters(null, java.util.List.of(1L), null)))
            .isInstanceOf(BadRequestException.class);
      assertThatThrownBy(() -> audience.of(Audience.SELLERS, new BroadcastFilters(null, null, java.util.List.of("LPG"))))
            .isInstanceOf(BadRequestException.class);
      assertThat(audience.of(Audience.SELLERS, new BroadcastFilters(java.util.List.of(5L), null, null)).sql())
            .contains("shop_brands");
   }
}
