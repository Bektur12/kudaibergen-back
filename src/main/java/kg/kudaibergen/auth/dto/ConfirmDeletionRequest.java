package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmDeletionRequest(
      @NotBlank(message = "Укажите код")
      @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр")
      String code) {
}
