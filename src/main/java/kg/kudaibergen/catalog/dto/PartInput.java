package kg.kudaibergen.catalog.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.catalog.entity.PartPosition;
import kg.kudaibergen.catalog.entity.PartSide;
import kg.kudaibergen.request.entity.PartCondition;

/**
 * Форма «Новая запчасть» (26). В черновике всё необязательно; публикация требует название, категорию,
 * состояние, цену, 1–6 фото и 1–20 машин. В PATCH null — «не менять», для списков [] — очистить.
 * mediaIds — по порядку, первое — главное фото.
 */
public record PartInput(
      @Size(max = 120, message = "Название — до 120 символов") String title,
      Long categoryId,
      PartCondition condition,
      @Positive(message = "Цена — целое число сом больше нуля") Integer price,
      @PositiveOrZero(message = "Количество не может быть отрицательным") Integer quantity,
      @Size(max = 60, message = "Производитель — до 60 символов") String manufacturer,
      @Size(max = 40, message = "Номер детали — до 40 символов") String oemNumber,
      PartSide side,
      PartPosition position,
      @Size(max = 6, message = "Не больше 6 фото") List<Long> mediaIds,
      @Size(max = 20, message = "Не больше 20 машин") List<@Valid FitmentInput> fitments) {

   /** «+ Машина»: марка → модель (или «Все модели») → годы от–до. */
   public record FitmentInput(
         @NotNull(message = "Выберите марку") Long brandId,
         Long modelId,
         @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год")
         Integer yearFrom,
         @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год")
         Integer yearTo) {
   }
}
