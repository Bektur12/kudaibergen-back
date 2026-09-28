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
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.request.entity.ReviewTag;

/** Тела запросов модуля «Найти запчасть». */
public final class RequestInputs {

   private RequestInputs() {
   }

   /**
    * «Отправить» на экране 06. Машина — из гаража (новую клиент сначала добавляет через POST /me/cars).
    * targetRowId — для «Ряду», targetShopId — для «Боксу».
    */
   public record CreateRequest(
         @NotNull(message = "Выберите машину") Long carId,
         @NotBlank(message = "Напишите, что нужно")
         @Size(min = 3, max = 200, message = "От 3 до 200 символов") String text,
         Long categoryId,
         @NotNull(message = "Выберите, кому отправить") RequestTarget target,
         Long targetRowId,
         Long targetShopId) {
   }

   /** Живой счётчик «Запрос получат 43 продавца по Toyota»: машина из гаража или просто марка. */
   public record RecipientsPreview(
         Long carId,
         Long brandId,
         @NotNull(message = "Выберите, кому отправить") RequestTarget target,
         Long targetRowId,
         Long targetShopId) {
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

   /** Ответ бокса (12, кнопки пуша 14). Для «Есть» обязательно состояние, остальное — по желанию. */
   public record Reply(
         @NotNull(message = "Ответьте «Есть» или «Нет»") ReplyAnswer answer,
         PartCondition condition,
         @Size(max = 300, message = "Не длиннее 300 символов") String message,
         @Positive(message = "Цена — целое число сом больше нуля") Integer price) {
   }
}
