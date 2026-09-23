package kg.kudaibergen.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.PartCategory;

public record CreateRequestRequest(
      @NotNull(message = "Укажите категорию") PartCategory category,
      @NotBlank(message = "Опишите нужную деталь") @Size(max = 2000) String description,
      Long vehicleId,
      @Size(max = 160) String carText,
      @PositiveOrZero Integer budgetMin,
      @PositiveOrZero Integer budgetMax,
      Boolean isUrgent) {
}
