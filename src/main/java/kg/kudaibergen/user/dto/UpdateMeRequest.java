package kg.kudaibergen.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.user.entity.Lang;

/** Частичное обновление: null — поле не меняется. */
public record UpdateMeRequest(
      @Size(min = 1, max = 120, message = "Имя от 1 до 120 символов")
      @Pattern(regexp = ".*\\S.*", message = "Имя не может быть пустым")
      String name,
      Lang lang) {
}
