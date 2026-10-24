package kg.kudaibergen.shop.dto;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Подсветка на карте (15): «Mercedes-Benz — в 6 рядах · 21 бокс · ближайший ряд 14, 170 м».
 * rowIds и containerIds — что подсветить; shopsCount — действующие магазины, openNowCount — из них открыты сейчас.
 * nearest* — ближайший пешком бокс от точки пользователя (fromSource — откуда считали: POINT, GPS, ENTRANCE);
 * все null, если таких магазинов нет. rows — «Toyota — 4 ряда»: ряды, где продают, со счётчиком контейнеров (09),
 * больше контейнеров — выше.
 */
public record MapHighlightDto(MapFilterKind kind, Long id, String name, List<Long> rowIds, List<Long> containerIds,
                              int shopsCount, int rowsCount, int openNowCount, @Nullable Long nearestRowId, @Nullable String nearestRow,
                              @Nullable Long nearestContainerId, @Nullable Integer nearestContainer, @Nullable Long nearestShopId,
                              @Nullable Integer nearestDistanceM, @Nullable Integer nearestMinutes, String fromSource,
                              List<RowCount> rows) {

   /** «Ряд 14 · 9 контейнеров с Toyota». */
   public record RowCount(Long rowId, String rowCode, String rowLabel, int containers) {
   }
}
