package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.garage.dto.BrandDto;

/** Чип «логотип + модель + годы»: label «Toyota Camry 50 · 2011–2017», «Lexus · все модели». modelId null — все модели. */
public record FitmentDto(BrandDto brand, Long modelId, String modelLabel, Integer yearFrom, Integer yearTo,
                         String label) {
}
