package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import kg.kudaibergen.user.entity.Lang;

/** Экран 01. lang — выбранный переключателем RU/KG, по нему текст SMS и язык нового пользователя. */
public record SendOtpRequest(
      @NotBlank(message = "Укажите номер телефона")
      @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
      String phone,
      Lang lang) {

   public Lang langOrDefault() {
      return lang == null ? Lang.RU : lang;
   }
}
