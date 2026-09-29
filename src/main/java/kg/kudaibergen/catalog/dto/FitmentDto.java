package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.garage.dto.BrandDto;
import org.springframework.lang.Nullable;

/** Чип «логотип + модель + годы»: label «Toyota Camry 50 · 2011–2017», «Lexus · все модели». modelId null — все модели. */
public record FitmentDto(BrandDto brand, @Nullable Long modelId, @Nullable String modelLabel, @Nullable Integer yearFrom, @Nullable Integer yearTo,
                         String label) {
}
