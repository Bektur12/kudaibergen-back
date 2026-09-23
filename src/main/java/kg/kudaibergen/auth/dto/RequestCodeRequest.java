package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RequestCodeRequest(
      @NotBlank(message = "Укажите номер телефона")
      @Pattern(regexp = "\\+996\\d{9}", message = "Формат номера: +996XXXXXXXXX")
      String phone) {
}
