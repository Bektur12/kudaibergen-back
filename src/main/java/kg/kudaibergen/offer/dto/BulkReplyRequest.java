package kg.kudaibergen.offer.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Массовый ответ: один шаблон (или текст) сразу на несколько запросов. */
public record BulkReplyRequest(
      @NotEmpty(message = "Выберите запросы") @Size(max = 100) List<Long> requestIds,
      Long templateId,
      @Size(max = 2000) String text,
      @PositiveOrZero Integer price,
      Short deliveryDays) {
}
