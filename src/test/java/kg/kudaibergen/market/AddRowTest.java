package kg.kudaibergen.market;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Продавец добавляет ряд, которого нет в списке: нормализация кода, найден / включён снова / создан. */
class AddRowTest {

   private MarketRowRepository rows;
   private ContainerRepository containers;
   private MarketMapService market;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      rows = mock(MarketRowRepository.class);
      containers = mock(ContainerRepository.class);
      market = new MarketMapService(mock(MapVersionRepository.class), rows, containers, new ObjectMapper(),
            mock(ObjectProvider.class));
      when(rows.findByCodeIgnoreCase(anyString())).thenReturn(List.of());
      when(containers.findByRowId(any())).thenReturn(List.of());
   }

   @Test
   void нормализацияКода() {
      assertThat(MarketMapService.normalizeRowCode("  ф ")).isEqualTo("Ф");
      assertThat(MarketMapService.normalizeRowCode("Ряд Ф")).isEqualTo("Ф");
      assertThat(MarketMapService.normalizeRowCode("ряд   14")).isEqualTo("14");
      assertThat(MarketMapService.normalizeRowCode("k")).isEqualTo("К");
      assertThat(MarketMapService.normalizeRowCode("ABCEHK")).isEqualTo("АВСЕНК");
      assertThat(MarketMapService.normalizeRowCode("mOPtxy")).isEqualTo("МОРТХУ");
      assertThat(MarketMapService.normalizeRowCode("жайма-3")).isEqualTo("ЖАЙМА-3");
      assertThat(MarketMapService.normalizeRowCode("Жайма  3")).isEqualTo("ЖАЙМА 3");
      assertThat(MarketMapService.normalizeRowCode("Ряды")).isEqualTo("РЯДЫ");
      assertThat(MarketMapService.normalizeRowCode("Q")).isEqualTo("Q");
   }

   @Test
   void плохойКод400() {
      for (String bad : new String[] {null, "", "  ", "ряд", "Ряд ", "-", "Ф!", "12345678901", "ряд 1/2"}) {
         assertThatThrownBy(() -> MarketMapService.normalizeRowCode(bad)).as(String.valueOf(bad))
               .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("BAD_ROW_CODE");
      }
   }

   @Test
   void естьАктивный_возвращаетсяОн() {
      MarketRow existing = row(5L, "Ф", true);
      when(rows.findByCodeIgnoreCase("Ф")).thenReturn(List.of(existing));
      Container container = new Container(5L, Side.NORTH, (short) 1, "c");
      when(containers.findByRowId(5L)).thenReturn(List.of(container));

      MarketMapService.RowResult result = market.addRow("ряд ф", 7L);
      assertThat(result.created()).isFalse();
      assertThat(result.row().id()).isEqualTo(5L);
      assertThat(result.row().containerCount()).isEqualTo(1);
      verify(rows, never()).save(any());
   }

   @Test
   void выключенныйВключается() {
      MarketRow existing = row(5L, "Ф", false);
      when(rows.findByCodeIgnoreCase("Ф")).thenReturn(List.of(existing));

      MarketMapService.RowResult result = market.addRow("Ф", 7L);
      assertThat(result.created()).isFalse();
      assertThat(existing.isActive()).isTrue();
      verify(rows, never()).save(any());
   }

   @Test
   void новыйРядБезФормы() {
      when(rows.maxSortOrder()).thenReturn((short) 41);
      when(rows.save(any())).thenAnswer(invocation -> {
         MarketRow saved = invocation.getArgument(0);
         ReflectionTestUtils.setField(saved, "id", 99L);
         return saved;
      });

      MarketMapService.RowResult result = market.addRow(" ряд ф ", 7L);
      assertThat(result.created()).isTrue();
      assertThat(result.row().id()).isEqualTo(99L);
      assertThat(result.row().code()).isEqualTo("Ф");
      assertThat(result.row().label()).isEqualTo("Ряд Ф");
      assertThat(result.row().type()).isEqualTo(RowType.ROW);
      assertThat(result.row().onMap()).isFalse();
      assertThat(result.row().containerCount()).isZero();
      verify(rows).save(org.mockito.ArgumentMatchers.argThat(row -> row.getSortOrder() == 42
            && row.getGeometry() == null && row.getQrToken().length() == 32 && row.isActive()));
   }

   private static MarketRow row(Long id, String code, boolean active) {
      MarketRow row = new MarketRow(code, "Ряд " + code, RowType.ROW, null, (short) 1, "tok-" + id);
      ReflectionTestUtils.setField(row, "id", id);
      row.setActive(active);
      return row;
   }
}
