package kg.kudaibergen.offer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateOfferRequest(
      @NotNull(message = "Укажите запрос") Long requestId,
      @PositiveOrZero Integer price,
      @Size(max = 2000) String comment,
      @Min(0) @Max(365) Short deliveryDays) {
}
