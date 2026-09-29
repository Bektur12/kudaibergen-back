package kg.kudaibergen.catalog.dto;

import java.util.List;

/**
 * Выдача (27): «24 запчасти для Camry 50» — total и appliedCar.displayName. appliedCar = null — машина
 * не выбрана. nextCursor = null — дальше ничего.
 */
public record PartSearchResultDto(List<PartCardDto> items, long total, AppliedCarDto appliedCar, String nextCursor) {
}
