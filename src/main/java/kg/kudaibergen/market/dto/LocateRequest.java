package kg.kudaibergen.market.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** GPS телефона. На сервере не сохраняется (ТЗ 6.2). */
public record LocateRequest(
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
      @PositiveOrZero Double accuracyM) {
}
