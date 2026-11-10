package kg.kudaibergen.market;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.market.dto.MapDto;
import kg.kudaibergen.market.dto.RowDetailDto;
import kg.kudaibergen.market.dto.RowDto;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.MapVersion;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ряд без формы на схеме (geometry = NULL) и выбор стороны для контейнера, добавленного продавцом. */
class RowWithoutShapeTest {

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
      MarketRow row14 = row(1L, "14", "{\"rect\":{\"x\":100,\"y\":400,\"w\":300,\"h\":50}}");
      MarketRow rowK = row(2L, "К", null);
      when(versions.findByCurrentTrue()).thenReturn(Optional.of(version));
      when(rows.findByActiveTrueOrderBySortOrder()).thenReturn(List.of(row14, rowK));
      when(containers.findByActiveTrue()).thenReturn(List.of(container(10L, 1L, 1), container(20L, 2L, 1),
            container(21L, 2L, 2)));
      ObjectProvider<ContainerTenants> tenants = mock(ObjectProvider.class);
      market = new MarketMapService(versions, rows, containers, new ObjectMapper(), tenants);
   }

   @Test
   void рядБезФормыЕстьВСпискеНоНеНаКарте() {
      List<RowDto> rows = market.rows();
      assertThat(rows).extracting(RowDto::code).containsExactly("14", "К");
      assertThat(rows).extracting(RowDto::onMap).containsExactly(true, false);
      assertThat(rows.get(1).containerCount()).isEqualTo(2);

      MapDto map = market.map();
      assertThat(map.rows()).extracting(MapDto.MapRowDto::code).containsExactly("14");
   }

   @Test
   void контейнерыРядаБезФормыВыбираются() {
      RowDetailDto detail = market.row(2L, null);
      assertThat(detail.sides()).flatExtracting(RowDetailDto.SideDto::containers)
            .extracting(RowDetailDto.ContainerSlotDto::number).containsExactly(1, 2);
      assertThat(detail.sides().get(0).containers().get(0).side()).isEqualTo(Side.NORTH);
      assertThat(market.container(20L).onMap()).isFalse();
      assertThat(market.location(20L).rowCode()).isEqualTo("К");
   }

   @Test
   void маршрутКРядуБезФормы409() {
      assertThatThrownBy(() -> market.route(20L, null, null, null, null, Lang.RU))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CONTAINER_NOT_ON_MAP");
      assertThat(market.route(10L, null, null, null, null, Lang.RU).distanceM()).isPositive();
      assertThat(market.walkMinutesFromEntrance(20L)).isEmpty();
      assertThat(market.nearest(new Point(0, 500), List.of(20L, 10L))).get()
            .extracting(n -> n.container().container().getId()).isEqualTo(10L);
      assertThat(market.nearest(new Point(0, 500), List.of(20L))).isEmpty();
   }

   @Test
   void qrРядаБезФормыБезТочки() {
      var qr = market.resolveQr("tok-2");
      assertThat(qr.x()).isNull();
      assertThat(qr.y()).isNull();
   }

   // ─────────────────────── сторона нового контейнера ───────────────────────

   @Test
   void переданнаяСторона() {
      MarketRow row = row(1L, "1", null);
      assertThat(MarketMapService.resolveSide(row, Set.of(Side.NORTH, Side.SOUTH), Side.SOUTH)).isEqualTo(Side.SOUTH);
      assertThatThrownBy(() -> MarketMapService.resolveSide(row, Set.of(), Side.EAST))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("WRONG_SIDE");
   }

   @Test
   void единственнаяСторонаРяда() {
      MarketRow row = row(1L, "15", null);
      assertThat(MarketMapService.resolveSide(row, Set.of(Side.NORTH), null)).isEqualTo(Side.NORTH);
      assertThat(MarketMapService.resolveSide(row, Set.of(Side.SOUTH), null)).isEqualTo(Side.SOUTH);
   }

   @Test
   void двеСтороны400() {
      MarketRow row = row(1L, "1", null);
      assertThatThrownBy(() -> MarketMapService.resolveSide(row, EnumSet.of(Side.NORTH, Side.SOUTH), null))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("SIDE_REQUIRED");
   }

   @Test
   void пустойРядПоУмолчанию() {
      assertThat(MarketMapService.resolveSide(row(1L, "К", null), Set.of(), null)).isEqualTo(Side.NORTH);
      assertThat(MarketMapService.resolveSide(new MarketRow("С", "Ряд С", RowType.STALL, null, (short) 1, "t"),
            Set.of(), null)).isEqualTo(Side.NORTH);
      assertThat(MarketMapService.resolveSide(new MarketRow("Ю", "Ряд Ю", RowType.VROW, null, (short) 1, "t"),
            Set.of(), null)).isEqualTo(Side.EAST);
   }

   private static MarketRow row(Long id, String code, String geometry) {
      MarketRow row = new MarketRow(code, "Ряд " + code, RowType.ROW, geometry, id.shortValue(), "tok-" + id);
      ReflectionTestUtils.setField(row, "id", id);
      return row;
   }

   private static Container container(Long id, Long rowId, int number) {
      Container container = new Container(rowId, Side.NORTH, (short) number, "c-" + id);
      ReflectionTestUtils.setField(container, "id", id);
      return container;
   }
}
