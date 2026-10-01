package kg.kudaibergen.chat.dto;

import kg.kudaibergen.chat.entity.ChatSide;

/**
 * Конверт событий Centrifugo, клиент различает по type:
 * <ul>
 *   <li>канал chat:{id}: MESSAGE — {@link MessageDto}; READ — {@link ReadPayload};</li>
 *   <li>канал inbox: CHAT — {@link ChatListItemDto} (обновлённая строка списка); UNREAD — {@link UnreadDto};
 *   REQUEST_STATS — статистика своего запроса (RequestStatsDto, экран 32).</li>
 * </ul>
 * TYPING клиенты публикуют в канал чата сами, без бэкенда.
 */
public record ChatEvent(String type, Object payload) {

   public static ChatEvent message(MessageDto message) {
      return new ChatEvent("MESSAGE", message);
   }

   public static ChatEvent read(ReadPayload read) {
      return new ChatEvent("READ", read);
   }

   public static ChatEvent chat(ChatListItemDto row) {
      return new ChatEvent("CHAT", row);
   }

   public static ChatEvent unread(UnreadDto unread) {
      return new ChatEvent("UNREAD", unread);
   }

   /** Сторона side прочитала всё до readMessageId. */
   public record ReadPayload(Long chatId, ChatSide side, long readMessageId) {
   }
}
