package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import kg.kudaibergen.user.entity.Lang;

/** Экран 02. lang — язык, с которым зарегистрируется новый пользователь (как на экране 01). */
public record VerifyOtpRequest(
      @NotBlank(message = "Укажите номер телефона")
      @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
      String phone,
      @NotBlank(message = "Укажите код")
      @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр")
      String code,
      Lang lang) {

   public Lang langOrDefault() {
      return lang == null ? Lang.RU : lang;
   }
}
