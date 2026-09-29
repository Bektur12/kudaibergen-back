package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.garage.dto.BrandDto;
import org.springframework.lang.Nullable;

/**
 * Машина, под которую подобрана выдача (27): заголовок «24 запчасти для Camry 50».
 * displayName — модель («Camry 50») или марка, если модель не выбрана; label — «Camry 50 · 2012».
 */
public record AppliedCarDto(BrandDto brand, @Nullable Long modelId, String displayName, @Nullable Integer year, String label) {
}
