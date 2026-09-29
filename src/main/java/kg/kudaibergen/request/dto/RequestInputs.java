package kg.kudaibergen.request.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

   private RequestInputs() {
   }

   /**
    * «Отправить» на экране 06. Машина — из гаража (новую клиент сначала добавляет через POST /me/cars).
    * targetRowIds — для «Рядам» (1–10), targetContainerIds — для «Контейнерам» (1–30).
    * duration — «Сколько ждать ответы», по умолчанию 30 минут. mediaIds — фото детали (POST /media/photos,
    * purpose=REQUEST), до 3.
    */
   public record CreateRequest(
         @NotNull(message = "Выберите машину") Long carId,
         @NotBlank(message = "Напишите, что нужно")
         @Size(min = 3, max = 200, message = "От 3 до 200 символов") String text,
         Long categoryId,
         @NotNull(message = "Выберите, кому отправить") RequestTarget target,
         List<Long> targetRowIds,
         List<Long> targetContainerIds,
         RequestDuration duration,
         @Size(max = MAX_PHOTOS, message = "Не больше 3 фото") List<Long> mediaIds) {
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
    * Ответ бокса (12, кнопки пуша 14). Для «Есть» обязательно состояние, остальное — по желанию.
    * partId — «Приложить товар из каталога»: своя опубликованная запчасть.
    * mediaIds — фото к «Есть» (POST /media/photos, purpose=REPLY), до 3.
    */
   public record Reply(
         @NotNull(message = "Ответьте «Есть» или «Нет»") ReplyAnswer answer,
         PartCondition condition,
         @Size(max = 300, message = "Не длиннее 300 символов") String message,
         @Positive(message = "Цена — целое число сом больше нуля") Integer price,
         Long partId,
         @Size(max = MAX_PHOTOS, message = "Не больше 3 фото") List<Long> mediaIds) {
   }
}
