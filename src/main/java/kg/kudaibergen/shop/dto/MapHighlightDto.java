package kg.kudaibergen.shop.dto;

import java.util.List;

/**
 * Подсветка на карте (15): «Mercedes-Benz — в 6 рядах · 21 бокс · ближайший ряд 14, 170 м».
 * rowIds и containerIds — что подсветить; shopsCount — действующие магазины, openNowCount — из них открыты сейчас.
 * nearest* — ближайший пешком бокс от точки пользователя (fromSource — откуда считали: POINT, GPS, ENTRANCE);
 * все null, если таких магазинов нет.
 */
public record MapHighlightDto(MapFilterKind kind, Long id, String name, List<Long> rowIds, List<Long> containerIds,
                              int shopsCount, int rowsCount, int openNowCount, Long nearestRowId, String nearestRow,
                              Long nearestContainerId, Integer nearestContainer, Long nearestShopId,
                              Integer nearestDistanceM, Integer nearestMinutes, String fromSource) {
}
