package kg.kudaibergen.chat;

import kg.kudaibergen.chat.entity.ChatSide;

/** События чата: публикуются в транзакции, в Centrifugo и пуши уходят после коммита (ChatRealtime). */
public final class ChatEvents {

   private ChatEvents() {
   }

   /** Новое сообщение. push = false — без пуша (карточка «Есть»: о ней уже сообщил модуль запросов). */
   public record MessageAdded(Long chatId, Long messageId, boolean push) {
   }

   /** Сторона дочитала до readMessageId. */
   public record Read(Long chatId, ChatSide side, long readMessageId) {
   }

   /** Изменилась строка списка (блокировка): обновить inbox обеих сторон. */
   public record Changed(Long chatId) {
   }
}
