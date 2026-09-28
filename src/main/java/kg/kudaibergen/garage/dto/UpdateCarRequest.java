package kg.kudaibergen.garage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Частичное обновление: null — поле не меняется, пустая строка — очистить
 * (для engine, vin, name).
 */
public record UpdateCarRequest(
      Long modelId,
      @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год") Integer year,
      @Size(max = 40, message = "Двигатель — до 40 символов") String engine,
      @Pattern(regexp = "|" + CarRules.VIN, message = CarRules.VIN_MESSAGE) String vin,
      @Size(max = 40, message = "Название — до 40 символов") String name) {
}
