package kg.kudaibergen.market.dto;

import java.util.List;

/** Поиск «Ряд, бокс или магазин» (экран 15). Магазины добавит модуль shops. */
public record MarketSearchDto(List<RowDto> rows, List<LocationDto> containers) {
}
