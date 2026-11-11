package kg.kudaibergen.market.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/**
 * Продавец не нашёл свой контейнер в сетке (10а) и добавляет его сам: номер и, если у ряда две стороны, сторона.
 * side не передан: у ряда контейнеры на одной стороне — она; на двух — 400 SIDE_REQUIRED; контейнеров нет —
 * NORTH (у вертикального ряда — EAST).
 */
public record AddContainerRequest(
      @NotNull(message = "Укажите номер контейнера")
      @Min(value = 1, message = "Номер контейнера — от 1 до 999")
      @Max(value = 999, message = "Номер контейнера — от 1 до 999") Integer number,
      @Nullable Side side) {
}
