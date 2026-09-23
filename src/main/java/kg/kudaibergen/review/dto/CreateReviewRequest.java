package kg.kudaibergen.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
      @NotNull(message = "Поставьте оценку") @Min(1) @Max(5) Short rating,
      @Size(max = 2000) String text,
      Long offerId) {
}
