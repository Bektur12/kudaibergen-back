package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.garage.dto.BrandDto;

/**
 * Машина, под которую подобрана выдача (27): заголовок «24 запчасти для Camry 50».
 * displayName — модель («Camry 50») или марка, если модель не выбрана; label — «Camry 50 · 2012».
 */
public record AppliedCarDto(BrandDto brand, Long modelId, String displayName, Integer year, String label) {
}
