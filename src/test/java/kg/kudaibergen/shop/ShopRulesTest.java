package kg.kudaibergen.shop;

import java.time.DayOfWeek;
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

   private final ShopHours hours = new ShopHours();

   private static Shop activeShop() {
      Shop shop = new Shop(1L, "Автодеталь Азамат", 10L);
      shop.verified();
      return shop;
   }

   @Test
   void действующийОткрытВсегдаБезЧасовРаботы() {
      OpenStateDto state = hours.state(activeShop());
      assertThat(state.openNow()).isTrue();
      assertThat(state.closedManually()).isFalse();
      assertThat(state.opensAt()).isNull();
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
