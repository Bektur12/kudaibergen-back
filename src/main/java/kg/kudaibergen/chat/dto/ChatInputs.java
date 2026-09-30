package kg.kudaibergen.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.chat.entity.QuickReply;

/** Тела запросов чата. */
public final class ChatInputs {

   private ChatInputs() {
   }

   /**
    * «Написать» (07, 29, 30, 37): чат покупателя с магазином (shopId) или мастером (masterId) — одно из двух.
    * requestId — чат по запросу (создан ответом «Есть»), serviceRequestId — по заявке (создан откликом «Могу помочь»),
    * без них — прямой чат из профиля. partId — с карточки запчасти (29): в чат уходит карточка товара.
    */
   public record OpenChat(Long shopId, Long requestId, Long partId, Long masterId, Long serviceRequestId) {
   }

   /**
    * Текст или быстрый ответ (quickReply). clientId — id сообщения с телефона: повтор из очереди
    * отправки без сети вернёт то же сообщение, а не создаст второе.
    */
   public record SendMessage(
         @Size(max = 4000, message = "Не длиннее 4000 символов") String text,
         QuickReply quickReply,
         @Size(max = 64, message = "clientId — до 64 символов") String clientId) {
   }

   /** Прочитано всё до этого сообщения включительно; null — до последнего. */
   public record Read(Long upToMessageId) {
   }

   public record ChatComplaint(@Size(max = 1000, message = "Не длиннее 1000 символов") String text) {
   }

   /** Свой шаблон ответа магазина. */
   public record Template(
         @NotBlank(message = "Напишите текст шаблона") @Size(max = 300, message = "Не длиннее 300 символов") String text,
         Integer sortOrder) {
   }
}
