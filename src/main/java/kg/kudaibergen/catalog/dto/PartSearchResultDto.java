package kg.kudaibergen.catalog.dto;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Выдача (27). С машиной — сначала подходящие ей (items[].fits = true), потом остальные: fitsTotal — «24 подходят
 * к Camry 50» (appliedCar.displayName), total — всего с остальными. appliedCar = null — машина не выбрана,
 * fitsTotal = null. nextCursor = null — дальше ничего.
 */
public record PartSearchResultDto(List<PartCardDto> items, long total, @Nullable Long fitsTotal,
                                  @Nullable AppliedCarDto appliedCar, @Nullable String nextCursor) {
}
