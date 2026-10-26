package kg.kudaibergen.request.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestDuration;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.request.entity.ReviewTag;

/** Тела запросов модуля «Найти запчасть». */
public final class RequestInputs {

   public static final int MAX_ROWS = 10;
   public static final int MAX_CONTAINERS = 30;
   public static final int MAX_PHOTOS = 3;
   public static final int MAX_TAGS = 5;

   private RequestInputs() {
   }

   /**
    * «Спросить весь рынок» (04). Машина — carId из гаража или modelId + year без гаража: мастер СТО ищет запчасть
    * для машины клиента, её в гараж не сохраняем.
    * Что нужно — фото (mediaIds, POST /media/photos purpose=REQUEST, до 3), голосовое (voiceMediaId,
    * POST /media/voices purpose=REQUEST) или текст 3–200 символов; хотя бы что-то одно. Без текста запрос
    * подписывается тем, что распознали в голосовом.
    * target по умолчанию — «Всему рынку»; targetRowIds — для «Рядам» (1–10), targetContainerIds — для
    * «Контейнерам» (1–30). duration по умолчанию — 30 минут. hintId — чип подсказки; categoryId тогда из подсказки.
    */
   public record CreateRequest(
         Long carId,
         Long modelId,
         @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год") Integer year,
         @Size(max = 200, message = "Не длиннее 200 символов") String text,
         Long categoryId,
         Long hintId,
         RequestTarget target,
         List<Long> targetRowIds,
         List<Long> targetContainerIds,
         RequestDuration duration,
         @Size(max = MAX_PHOTOS, message = "Не больше 3 фото") List<Long> mediaIds,
         Long voiceMediaId) {
   }

   /** «Сказали «есть», а не было» (08): какой бокс сказал «Есть». */
   public record FalseHave(@NotNull(message = "Выберите продавца") Long shopId) {
   }

   /** «Продлить» (32): на 30, 60 или 180 минут, до 3 раз. */
   public record Extend(@NotNull(message = "Выберите, на сколько продлить") Integer minutes) {
   }

   /** «Отправить всему рынку» (20, 32). Пока расширить можно только до всего рынка. */
   public record Widen(RequestTarget target) {
   }

   /**
    * «Купил — закрыть запрос» (09) или просто «Закрыть запрос» (20). shopId — у кого купил;
    * stars и tags — оценка этому боксу (необязательна).
    */
   public record CloseRequest(
         Long shopId,
         @Min(value = 1, message = "От 1 до 5 звёзд") @Max(value = 5, message = "От 1 до 5 звёзд") Integer stars,
         List<ReviewTag> tags) {
   }

   /**
    * Ответ бокса (13, 14, кнопки пуша 12). Для «Есть» обязательна цена, остальное — по желанию.
    * voiceMediaId — голосовое продавца (POST /media/voices, purpose=REPLY); original, manufacturer и tags —
    * из его разбора (reply в ответе загрузки), «Новая / Б/у» и «Оригинал / Копия» продавец может поправить.
    * partId — своя опубликованная запчасть; mediaIds — фото к «Есть» (purpose=REPLY), до 3.
    */
   public record Reply(
         @NotNull(message = "Ответьте «Есть» или «Нет»") ReplyAnswer answer,
         PartCondition condition,
         @Size(max = 300, message = "Не длиннее 300 символов") String message,
         @Positive(message = "Цена — целое число сом больше нуля") Integer price,
         Long partId,
         @Size(max = MAX_PHOTOS, message = "Не больше 3 фото") List<Long> mediaIds,
         Long voiceMediaId,
         Boolean original,
         @Size(max = 40, message = "Производитель — до 40 символов") String manufacturer,
         @Size(max = MAX_TAGS, message = "Не больше 5 чипов") List<@Size(max = 30) String> tags) {
   }
}
