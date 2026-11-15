package kg.kudaibergen.master;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.OfflinePackDto.PackMaster;
import kg.kudaibergen.master.dto.OfflinePackDto.PackServiceType;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.master.entity.ServiceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadsideRulesTest {

   // перевал Тоо-Ашуу и точки вдоль трассы Бишкек–Ош
   private static final double PASS_LAT = 42.335;
   private static final double PASS_LNG = 73.807;

   private static ServiceType type(String code, int defaultRadius, int maxRadius) {
      ServiceType type = BeanUtils.instantiateClass(ServiceType.class);
      ReflectionTestUtils.setField(type, "code", code);
      ReflectionTestUtils.setField(type, "defaultRadiusKm", (short) defaultRadius);
      ReflectionTestUtils.setField(type, "maxRadiusKm", (short) maxRadius);
      return type;
   }

   private static RoadsideService.RoadsideRow row(long id, long owner, double lat, double lng, int radiusKm, String... services) {
      return new RoadsideService.RoadsideRow(owner, new PackMaster(id, "Мастер " + id, "+996700000" + (100 + id), lat, lng,
            "трасса", List.of(services), true, radiusKm, BigDecimal.ZERO, 0, false, List.of(1L),
            List.of(CarOrigin.JAPAN)));
   }

   @Test
   void расширениеРадиуса() {
      // обычные услуги: +5 км до 50
      assertThat(ServiceRequest.nextRadius(5, 50)).isEqualTo(10);
      assertThat(ServiceRequest.nextRadius(48, 50)).isEqualTo(50);
      // эвакуатор, выездной: вдвое до 200
      assertThat(ServiceRequest.nextRadius(15, 200)).isEqualTo(30);
      assertThat(ServiceRequest.nextRadius(30, 200)).isEqualTo(60);
      assertThat(ServiceRequest.nextRadius(60, 200)).isEqualTo(120);
      assertThat(ServiceRequest.nextRadius(120, 200)).isEqualTo(200);
      assertThat(ServiceRequest.nextRadius(2, 200)).isEqualTo(7);
   }

   @Test
   void радиусЗаявкиПоУслуге() {
      ServiceType tow = type("TOW_TRUCK", 10, 200);
      ServiceType wash = type("CAR_WASH", 5, 50);
      assertThat(ServiceRequestService.radius(null, tow)).isEqualTo(10);
      assertThat(ServiceRequestService.radius(150, tow)).isEqualTo(150);
      assertThatThrownBy(() -> ServiceRequestService.radius(60, wash)).isInstanceOf(BadRequestException.class);
   }

   @Test
   void радиусМастераПоУслугам() {
      assertThat(MasterService.maxRadiusKm(List.of(type("CAR_WASH", 5, 50)))).isEqualTo(30);
      assertThat(MasterService.maxRadiusKm(List.of())).isEqualTo(30);
      assertThat(MasterService.maxRadiusKm(List.of(type("CAR_WASH", 5, 50), type("TOW_TRUCK", 10, 200))))
            .isEqualTo(200);
   }

   @Test
   void точкаСнятаБезСети() {
      Instant now = Instant.parse("2026-11-15T09:00:00Z");
      assertThat(ServiceRequestService.locatedAt(null, now)).isNull();
      assertThat(ServiceRequestService.locatedAt(now.minusSeconds(60), now)).isNull();
      assertThat(ServiceRequestService.locatedAt(now.plusSeconds(3600), now)).isNull();
      Instant pass = now.minusSeconds(2 * 3600);
      assertThat(ServiceRequestService.locatedAt(pass, now)).isEqualTo(pass);
   }

   @Test
   void ктоРядомБезОбрезкиПоРадиусу() {
      List<RoadsideService.RoadsideRow> rows = List.of(
            row(1, 11, 42.87, 74.59, 30, "TOW_TRUCK"),     // Бишкек, ~85 км, радиус 30 — не доедет
            row(2, 12, 42.81, 73.85, 200, "TOW_TRUCK"),    // Сосновка, ~50 км, радиус 200 — доедет
            row(3, 13, 42.35, 73.80, 5, "TIRE_SERVICE"),   // у самого перевала, но другая услуга
            row(4, 14, 42.33, 73.81, 5, "TOW_TRUCK"),      // у перевала — доедет
            row(5, 99, 42.335, 73.807, 50, "TOW_TRUCK"));  // свой профиль — не показываем

      List<RoadsideService.RoadsideMatch> found = RoadsideService.rank(rows, 99L, PASS_LAT, PASS_LNG, "TOW_TRUCK", null, 20);

      assertThat(found).extracting(r -> r.row().master().id()).containsExactly(4L, 2L, 1L);
      assertThat(found.get(0).reachesYou()).isTrue();
      assertThat(found.get(2).reachesYou()).isFalse();
      assertThat(found.get(2).distanceM()).isGreaterThan(50_000);
      assertThat(found).allMatch(r -> r.worksWithCar() == null);
   }

   @Test
   void ктоРядомСМашиной() {
      List<RoadsideService.RoadsideRow> rows = List.of(row(1, 11, 42.34, 73.81, 50, "CAR_REPAIR"));
      ServiceRequest.CarSnapshot toyota = new ServiceRequest.CarSnapshot(null, 1L, null, null, null, null, CarOrigin.JAPAN);
      ServiceRequest.CarSnapshot bmw = new ServiceRequest.CarSnapshot(null, 2L, null, null, null, null, CarOrigin.EUROPE);
      assertThat(RoadsideService.rank(rows, 99L, PASS_LAT, PASS_LNG, null, toyota, 20).get(0).worksWithCar()).isTrue();
      assertThat(RoadsideService.rank(rows, 99L, PASS_LAT, PASS_LNG, null, bmw, 20).get(0).worksWithCar()).isFalse();
   }

   @Test
   void версияСправочника() {
      List<PackServiceType> types = List.of(new PackServiceType("TOW_TRUCK", "Эвакуатор", "Эвакуатор", "tow", true, 200));
      List<PackMaster> masters = List.of(row(1, 11, 42.87, 74.59, 30, "TOW_TRUCK").master());
      String version = RoadsideService.version(types, masters);
      assertThat(RoadsideService.version(types, masters)).isEqualTo(version).hasSize(16);
      List<PackMaster> changed = List.of(row(1, 11, 42.87, 74.59, 40, "TOW_TRUCK").master());
      assertThat(RoadsideService.version(types, changed)).isNotEqualTo(version);
   }

   @Test
   void расстояниеНаТрассе() {
      assertThat(ServiceNotifier.km(85_400)).isEqualTo("85 км");
      assertThat(ServiceNotifier.km(9_640)).isEqualTo("9,6 км");
   }
}
