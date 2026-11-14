package kg.kudaibergen.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.market.dto.MapDto;
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

/** Контейнеры с точными координатами (разметка по карте) и ряды без них — по-старому, равномерно. */
class ContainerCoordinatesTest {

   private MarketMapService market;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      MapVersionRepository versions = mock(MapVersionRepository.class);
      MarketRowRepository rows = mock(MarketRowRepository.class);
      ContainerRepository containers = mock(ContainerRepository.class);
      MapVersion version = new MapVersion(1, "[[0,0],[1000,0],[1000,1000],[0,1000]]", "[]",
            "[{\"points\":[[0,500],[1000,500]],\"width\":10}]", "[{\"x\":0,\"y\":500,\"label\":\"Главный\"}]",
            "[]", "[]", "[]", BigDecimal.valueOf(0.25), null);
      // ряд 5 — северная сторона прохода y=500: ось по проходу, контейнеры с координатами
      MarketRow row5 = row(1L, "5",
            "{\"polygon\":[[100,470],[500,470],[500,500],[100,500]],\"axis\":[[100,500],[500,500]],\"halfWidth\":30}");
      // ряд 14 — без координат, раскладка равномерная
      MarketRow row14 = row(2L, "14", "{\"rect\":{\"x\":100,\"y\":600,\"w\":300,\"h\":50}}");
      when(versions.findByCurrentTrue()).thenReturn(Optional.of(version));
      when(rows.findByActiveTrueOrderBySortOrder()).thenReturn(List.of(row5, row14));
      when(containers.findByActiveTrue()).thenReturn(List.of(
            placed(10L, 1L, 1, 1, 200, 485), placed(11L, 1L, 2, 2, 212, 485), placed(12L, 1L, 3, 3, 236, 486),
            container(20L, 2L, 1, 1), container(21L, 2L, 2, 2)));
      ObjectProvider<ContainerTenants> tenants = mock(ObjectProvider.class);
      when(tenants.getIfAvailable()).thenReturn(ids -> java.util.Map.of(11L,
            new ContainerTenants.Tenant(7L, "Toyota запчасти", null, java.util.Set.of())));
      market = new MarketMapService(versions, rows, containers, new ObjectMapper(), tenants);
   }

   @Test
   void контейнерСтоитВСвоейТочке() {
      MapDto.MapContainerDto first = mapContainer(10L);
      assertThat(first.x()).isEqualTo(200);
      assertThat(first.y()).isEqualTo(485);
      assertThat(first.angle()).isEqualTo(0);
      // ячейка — медиана шагов между соседями (12 и 24 → 24), но не шире равномерной (400/3)
      assertThat(first.length()).isCloseTo(24, within(0.01));
      assertThat(mapContainer(12L).x()).isEqualTo(236);
   }

   @Test
   void магазинВКонтейнереНаКарте() {
      assertThat(mapContainer(11L).shop().shopId()).isEqualTo(7L);
      assertThat(mapContainer(11L).shop().name()).isEqualTo("Toyota запчасти");
      assertThat(mapContainer(10L).shop()).isNull();
      assertThat(market.map().shopsTag()).isNotEqualTo("1"); // «1» — отпечаток карты без магазинов
   }

   @Test
   void маршрутДоДвериНаПроходе() {
      var route = market.route(11L, null, null, null, null, Lang.RU);
      // от входа (0,500) по проходу до двери (212,500): 212 px × 0.25 м
      assertThat(route.distanceM()).isBetween(51, 55);
   }

   @Test
   void рядБезКоординатРаскладываетсяРавномерно() {
      MapDto.MapContainerDto a = mapContainer(20L);
      MapDto.MapContainerDto b = mapContainer(21L);
      assertThat(a.x()).isEqualTo(175);
      assertThat(b.x()).isEqualTo(325);
      assertThat(a.length()).isEqualTo(150);
   }

   private MapDto.MapContainerDto mapContainer(long id) {
      return market.map().rows().stream().flatMap(r -> r.containers().stream())
            .filter(c -> c.id() == id).findFirst().orElseThrow();
   }

   private static MarketRow row(Long id, String code, String geometry) {
      MarketRow row = new MarketRow(code, "Ряд " + code, RowType.ROW, geometry, id.shortValue(), "tok-" + id);
      ReflectionTestUtils.setField(row, "id", id);
      return row;
   }

   private static Container container(Long id, Long rowId, int number, int pos) {
      Container container = new Container(rowId, Side.NORTH, (short) number, "c-" + id);
      ReflectionTestUtils.setField(container, "id", id);
      ReflectionTestUtils.setField(container, "posInRow", (short) pos);
      return container;
   }

   private static Container placed(Long id, Long rowId, int number, int pos, double x, double y) {
      Container container = container(id, rowId, number, pos);
      ReflectionTestUtils.setField(container, "x", x);
      ReflectionTestUtils.setField(container, "y", y);
      return container;
   }
}
