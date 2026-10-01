package kg.kudaibergen.master;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;

import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.master.entity.ServiceOffer;
import kg.kudaibergen.master.entity.OfferAnswer;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterRulesTest {

   private static Instant at(int hour, int minute) {
      return ZonedDateTime.of(2026, 9, 30, hour, minute, 0, 0, ClockConfig.MARKET_ZONE).toInstant();
   }

   @Test
   void срокПоУслуге() {
      assertThat(ServiceDuration.ofMinutes(15)).isEqualTo(ServiceDuration.MIN_15);
      assertThat(ServiceDuration.ofMinutes(30)).isEqualTo(ServiceDuration.MIN_30);
      assertThat(ServiceDuration.MIN_15.expiresAt(at(10, 0))).isEqualTo(at(10, 15));
      assertThat(ServiceDuration.END_OF_DAY.expiresAt(at(10, 0))).isEqualTo(at(17, 0));
   }

   @Test
   void расстояниеПоПрямой() {
      // ~1 км на север в Бишкеке
      assertThat(Distances.meters(42.845, 74.625, 42.854, 74.625)).isBetween(990, 1010);
      assertThat(Distances.meters(42.845, 74.625, 42.845, 74.625)).isZero();
   }

   @Test
   void маркаИСтранаМашины() {
      Master master = new Master(1L, "СТО Ходовик", "Садыгалиева 41", 42.845, 74.625);
      master.replaceBrands(false, Set.of(1L));
      master.replaceOrigins(Set.of(CarOrigin.JAPAN, CarOrigin.UNKNOWN));
      assertThat(master.getOrigins()).containsExactly(CarOrigin.JAPAN);
      assertThat(master.worksWith(1L, CarOrigin.JAPAN)).isTrue();
      assertThat(master.worksWith(1L, CarOrigin.UNKNOWN)).isTrue();
      assertThat(master.worksWith(1L, CarOrigin.USA)).isFalse();
      assertThat(master.worksWith(2L, CarOrigin.JAPAN)).isFalse();

      master.replaceBrands(true, Set.of());
      master.replaceOrigins(Set.of());
      assertThat(master.worksWith(99L, CarOrigin.USA)).isTrue();
   }

   @Test
   void текстПушаОтклика() {
      ServiceOffer offer = new ServiceOffer(1L, 2L, OfferAnswer.CAN_HELP, 1500, at(15, 0), "Подъеду с домкратом", at(12, 0));
      assertThat(ServiceNotifier.offerBody(offer, Lang.RU)).isEqualTo("от 1 500 сом · 15:00 — «Подъеду с домкратом»");
      ServiceOffer notMine = new ServiceOffer(1L, 2L, OfferAnswer.NOT_MINE, 1500, at(15, 0), "x", at(12, 0));
      assertThat(notMine.getPriceFrom()).isNull();
      assertThat(ServiceNotifier.km(782)).isEqualTo("782 м");
      assertThat(ServiceNotifier.km(1240)).isEqualTo("1,2 км");
      assertThat(ServiceNotifier.offersWord(1)).isEqualTo("отклик");
      assertThat(ServiceNotifier.offersWord(3)).isEqualTo("отклика");
      assertThat(ServiceNotifier.offersWord(12)).isEqualTo("откликов");
   }

   @Test
   void подписьДвигателя() {
      assertThat(ServiceRequestMapper.engine(new java.math.BigDecimal("2.5"), kg.kudaibergen.garage.entity.FuelType.PETROL))
            .isEqualTo("2.5 бензин");
      assertThat(ServiceRequestMapper.engine(null, kg.kudaibergen.garage.entity.FuelType.DIESEL)).isEqualTo("дизель");
      assertThat(ServiceRequestMapper.engine(null, null)).isNull();
   }
}
