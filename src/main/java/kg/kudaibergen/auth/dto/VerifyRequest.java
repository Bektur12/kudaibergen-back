package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRequest(
      @NotBlank(message = "Укажите номер телефона")
      @Pattern(regexp = "\\+996\\d{9}", message = "Формат номера: +996XXXXXXXXX")
      String phone,
      @NotBlank(message = "Укажите код")
      @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр")
      String code) {
}
