package kg.kudaibergen.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.market.dto.RouteDto;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.MapVersion;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Маршрут от геолокации: на рынке — от человека, рядом — через ближайший по пути вход, далеко — от входа. */
class RouteFromGpsTest {

   private static final double LAT0 = 42.88;
   private static final double LON0 = 74.52;
   /** 4 единицы схемы на метр, центр схемы (500, 500) — в LAT0/LON0, ось y вниз. */
   private static final String AFFINE = "{\"lat0\":42.88,\"lon0\":74.52,\"a\":4,\"b\":0,\"c\":500,\"d\":0,\"e\":-4,\"f\":500}";

   private MarketMapService market;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      MapVersionRepository versions = mock(MapVersionRepository.class);
      MarketRowRepository rows = mock(MarketRowRepository.class);
      ContainerRepository containers = mock(ContainerRepository.class);
      // квадратный рынок, один проход с запада на восток, входы на обоих концах
      MapVersion version = new MapVersion(1, "[[0,0],[1000,0],[1000,1000],[0,1000]]", "[]",
            "[{\"points\":[[0,500],[1000,500]],\"width\":10}]",
            "[{\"x\":0,\"y\":500,\"label\":\"Западный\"},{\"x\":1000,\"y\":500,\"label\":\"Восточный\"}]",
            "[]", "[]", "[]", BigDecimal.valueOf(0.25), AFFINE);
      MarketRow row = new MarketRow("5", "Ряд 5", RowType.ROW,
            "{\"polygon\":[[600,470],[1000,470],[1000,500],[600,500]],\"axis\":[[600,500],[1000,500]],\"halfWidth\":30}",
            (short) 1, "tok-1");
      ReflectionTestUtils.setField(row, "id", 1L);
      Container box = new Container(1L, Side.NORTH, (short) 1, "c-1");
      ReflectionTestUtils.setField(box, "id", 10L);
      ReflectionTestUtils.setField(box, "posInRow", (short) 1);
      ReflectionTestUtils.setField(box, "x", 900.0);
      ReflectionTestUtils.setField(box, "y", 485.0);
      when(versions.findByCurrentTrue()).thenReturn(Optional.of(version));
      when(rows.findByActiveTrueOrderBySortOrder()).thenReturn(List.of(row));
      when(containers.findByActiveTrue()).thenReturn(List.of(box));
      ObjectProvider<ContainerTenants> tenants = mock(ObjectProvider.class);
      market = new MarketMapService(versions, rows, containers, new ObjectMapper(), tenants);
   }

   @Test
   void рядомСРынком_черезБлижайшийВходИОтЧеловека() {
      // 25 м восточнее восточного входа (схема x = 1100): идти через восточный, а не через главный западный
      RouteDto route = market.route(10L, null, lat(500), lon(1100), null, Lang.RU);
      assertThat(route.fromSource()).isEqualTo(RouteDto.FromSource.GPS);
      assertThat(route.polyline().get(0)).containsExactly(1100, 500);
      assertThat(route.polyline().get(1)[0]).isCloseTo(1000, within(0.5));
      // 100 px до входа + 100 px по проходу до двери (900, 500) = 50 м
      assertThat(route.distanceM()).isBetween(48, 52);
   }

   @Test
   void наРынке_отЧеловека() {
      RouteDto route = market.route(10L, null, lat(500), lon(300), null, Lang.RU);
      assertThat(route.fromSource()).isEqualTo(RouteDto.FromSource.GPS);
      assertThat(route.distanceM()).isBetween(148, 152);
   }

   @Test
   void далеко_отВходаБезЛинииОтЧеловека() {
      // 2,5 км к востоку — человек едет: маршрут от входа, который ему ближе по пути
      RouteDto route = market.route(10L, null, lat(500), lon(1000 + 10_000), null, Lang.RU);
      assertThat(route.fromSource()).isEqualTo(RouteDto.FromSource.ENTRANCE);
      assertThat(route.from()[0]).isCloseTo(1000, within(0.5));
      assertThat(route.distanceM()).isBetween(23, 27);
   }

   @Test
   void безГеолокации_отБлижайшегоКонтейнеруВхода() {
      // контейнер у восточного края — от восточного входа (25 м), а не от главного западного (225 м)
      RouteDto route = market.route(10L, null, null, null, null, Lang.RU);
      assertThat(route.fromSource()).isEqualTo(RouteDto.FromSource.ENTRANCE);
      assertThat(route.from()[0]).isCloseTo(1000, within(0.5));
      assertThat(route.distanceM()).isBetween(23, 27);
      assertThat(market.walkMinutesFromEntrance(10L)).contains(1);
   }

   @Test
   void выбранныйВход_отНего() {
      RouteDto route = market.route(10L, null, null, null, 0, Lang.RU);
      assertThat(route.from()[0]).isCloseTo(0, within(0.5));
   }

   @Test
   void ближайшийВходДляПодсветки() {
      assertThat(market.start(null, lat(500), lon(1100), null).point().x()).isCloseTo(1000, within(0.5));
      assertThat(market.start(null, lat(500), lon(-100), null).point().x()).isCloseTo(0, within(0.5));
   }

   /** Схема → GPS по той же аффинной матрице. */
   private static double lon(double x) {
      double east = (x - 500) / 4;
      return LON0 + east / (111_320 * Math.cos(Math.toRadians(LAT0)));
   }

   private static double lat(double y) {
      double north = (500 - y) / 4;
      return LAT0 + north / 110_574;
   }
}
