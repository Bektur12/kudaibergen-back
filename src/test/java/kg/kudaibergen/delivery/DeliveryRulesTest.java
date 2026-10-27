package kg.kudaibergen.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.delivery.dto.DeliveryTariffsDto;
import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeliveryRulesTest {

   private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
   private static final Instant MORNING = ZonedDateTime.of(2026, 10, 26, 11, 5, 0, 0, BISHKEK).toInstant();
   private static final Instant EVENING = ZonedDateTime.of(2026, 10, 26, 17, 45, 0, 0, BISHKEK).toInstant();

   private final DeliveryProperties properties = new DeliveryProperties("Ылдам Экспресс", null, 350, 400,
         LocalTime.of(17, 30), "996552599599", "+996552599599");
   private final DeliveryTariffs tariffs = new DeliveryTariffs(properties, Clock.fixed(MORNING, BISHKEK));

   @Test
   void tariffs() {
      assertThat(tariffs.of(DeliveryRegion.BISHKEK, null)).isEqualTo(new DeliveryTariffs.Tariff(null, 0));
      assertThat(tariffs.of(DeliveryRegion.OSH, null)).isEqualTo(new DeliveryTariffs.Tariff(350, 1));
      assertThat(tariffs.of(DeliveryRegion.BATKEN, null)).isEqualTo(new DeliveryTariffs.Tariff(400, 2));
      assertThat(tariffs.of(DeliveryRegion.OSH, DeliveryDistrict.CHON_ALAY)).isEqualTo(new DeliveryTariffs.Tariff(400, 2));
      assertThat(tariffs.of(DeliveryRegion.JALAL_ABAD, DeliveryDistrict.AKSY)).isEqualTo(new DeliveryTariffs.Tariff(400, 2));
      assertThatThrownBy(() -> tariffs.of(DeliveryRegion.NARYN, DeliveryDistrict.ALAY))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("BAD_DISTRICT");
   }

   @Test
   void tariffGridInDesignOrder() {
      DeliveryTariffsDto dto = tariffs.dto();
      assertThat(dto.regions()).extracting(DeliveryTariffsDto.RegionDto::name).containsExactly("Бишкек",
            "Чуйская обл.", "Ош", "Джалал-Абад", "Иссык-Куль", "Нарын", "Талас", "Баткен");
      assertThat(dto.regions().get(2).districts()).extracting(DeliveryTariffsDto.DistrictDto::name)
            .containsExactly("Чон-Алай", "Алай (Гульчо)");
      assertThat(dto.regions().get(3).districts()).extracting(DeliveryTariffsDto.DistrictDto::name)
            .containsExactly("Аксы (Кербен)", "Ала-Бука");
      assertThat(dto.cutoff()).isEqualTo("17:30");
      assertThat(dto.remoteNote()).isEqualTo("Чон-Алай, Аксы (Кербен), Ала-Бука и Алай (Гульчо) — отдалённые: 2 дня, 400 с");
   }

   @Test
   void cutoff() {
      assertThat(tariffs.pickupToday(MORNING)).isTrue();
      assertThat(tariffs.pickupToday(EVENING)).isFalse();
      // упаковали вчера после отсечки — курьер заберёт сегодня, а не «завтра»
      Instant yesterdayEvening = ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, BISHKEK).toInstant();
      assertThat(tariffs.pickupToday(yesterdayEvening)).isTrue();
      assertThat(tariffs.cutoffAt()).isEqualTo(ZonedDateTime.of(2026, 10, 26, 17, 30, 0, 0, BISHKEK).toInstant());
   }

   @Test
   void statusFlow() {
      DeliveryOrder order = order();
      assertThatThrownBy(() -> order.pickedUp(MORNING)).isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("BAD_STATUS");
      order.packed(new BigDecimal("3.0"), false, MORNING);
      assertThatThrownBy(() -> order.packed(BigDecimal.ONE, true, MORNING)).isInstanceOf(ConflictException.class);
      order.courierOrdered(MORNING);
      order.pickedUp(MORNING);
      order.delivered(MORNING);
      assertThat(order.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
      assertThatThrownBy(() -> order.cancel(MORNING)).isInstanceOf(ConflictException.class);
   }

   @Test
   void numberFromId() {
      DeliveryOrder order = order();
      ReflectionTestUtils.setField(order, "id", 123L);
      order.assignNumber();
      assertThat(order.getNumber()).isEqualTo("KDB-000123");
   }

   @Test
   void phones() {
      assertThat(DeliveryService.normalizePhone("0555 123 456")).isEqualTo("+996555123456");
      assertThat(DeliveryService.normalizePhone("+996 555 12 34 56")).isEqualTo("+996555123456");
      assertThat(DeliveryService.normalizePhone("555123456")).isEqualTo("+996555123456");
      assertThatThrownBy(() -> DeliveryService.normalizePhone("12345")).isInstanceOf(BadRequestException.class);
      assertThat(DeliveryView.phone("+996555123456")).isEqualTo("+996 555 123 456");
      assertThat(DeliveryView.kg(new BigDecimal("3.0"))).isEqualTo("3");
      assertThat(DeliveryView.kg(new BigDecimal("2.5"))).isEqualTo("2,5");
   }

   @Test
   void yldamTemplate() {
      DeliveryOrder order = order();
      ReflectionTestUtils.setField(order, "id", 123L);
      order.assignNumber();
      order.packed(new BigDecimal("3.0"), false, MORNING);
      DeliveryView view = new DeliveryView(null, null, null, null, null, tariffs);
      DeliveryView.Seller seller = new DeliveryView.Seller(1L, "Автодеталь Азамат", "Азамат", "Ряд 14 · конт. 12",
            "рынок Кудайберген, ряд 14, конт. 12", "+996700112233");
      String template = view.template(order, seller);
      assertThat(template).isEqualTo(String.join("\n",
            "📋Данные для оформления заказа",
            "Отправитель:",
            "Имя: Азамат",
            "Адрес: рынок Кудайберген, ряд 14, конт. 12",
            "Телефон: +996 700 112 233",
            "Получатель:",
            "Имя: Бакыт",
            "Адрес: ул. Ленина 45, кв. 12",
            "Телефон: +996 555 123 456",
            "Район / населённый пункт: г. Ош",
            "Посылка:",
            "Размер / вес: 3 кг",
            "Хрупкость: Нет",
            "Примечание: автозапчасть, заказ KDB-000123"));
      assertThat(view.whatsappUrl("a b")).isEqualTo("https://wa.me/996552599599?text=a%20b");
   }

   private static DeliveryOrder order() {
      return new DeliveryOrder(7L, 1L, 2L, "Стойки передние KYB, пара", 3200, DeliveryRegion.OSH, null,
            "ул. Ленина 45, кв. 12", "Бакыт", "+996555123456", 350, 1, MORNING);
   }
}
