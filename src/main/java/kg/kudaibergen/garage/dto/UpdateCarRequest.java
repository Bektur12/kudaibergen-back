package kg.kudaibergen.garage.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;

/**
 * Частичное обновление: null — поле не меняется, пустая строка — очистить
 * (для engine, vin, name).
 */
public record UpdateCarRequest(
      Long modelId,
      @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год") Integer year,
      @Size(max = 40, message = "Двигатель — до 40 символов") String engine,
      @Pattern(regexp = "|" + CarRules.VIN, message = CarRules.VIN_MESSAGE) String vin,
      @Size(max = 40, message = "Название — до 40 символов") String name,
      @DecimalMin(value = "0.5", message = "Объём — от 0.5 до 9.9 л") @DecimalMax(value = "9.9", message = "Объём — от 0.5 до 9.9 л")
      BigDecimal engineVolume,
      FuelType fuel,
      CarOrigin origin) {
}
