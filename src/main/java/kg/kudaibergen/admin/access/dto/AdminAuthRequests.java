package kg.kudaibergen.admin.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;

/** Тела запросов входа в админку. */
public final class AdminAuthRequests {

   /** Пароль: 10–72 символа, хотя бы одна буква и одна цифра (72 — предел bcrypt). */
   public static final String PASSWORD_RULE = "^(?=.*\\p{L})(?=.*\\d).{10,72}$";

   private AdminAuthRequests() {
   }

   /** Шаг 1: телефон и пароль. Верно — на телефон уходит SMS-код. */
   public record AdminLoginRequest(
         @NotBlank(message = "Укажите номер телефона")
         @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
         String phone,
         @NotBlank(message = "Укажите пароль")
         @Size(max = 72, message = "Пароль не длиннее 72 символов")
         String password) {
   }

   /** Шаг 2: код из SMS. */
   public record AdminVerifyRequest(
         @NotBlank(message = "Укажите номер телефона")
         @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
         String phone,
         @NotBlank(message = "Укажите код")
         @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр")
         String code) {
   }

   /** Код для первого пароля или сброса забытого. */
   public record AdminPasswordCodeRequest(
         @NotBlank(message = "Укажите номер телефона")
         @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
         String phone) {
   }

   public record AdminSetPasswordRequest(
         @NotBlank(message = "Укажите номер телефона")
         @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE)
         String phone,
         @NotBlank(message = "Укажите код")
         @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр")
         String code,
         @NotBlank(message = "Укажите пароль")
         @Pattern(regexp = PASSWORD_RULE, message = "Пароль: от 10 символов, хотя бы одна буква и одна цифра")
         String password) {
   }
}
