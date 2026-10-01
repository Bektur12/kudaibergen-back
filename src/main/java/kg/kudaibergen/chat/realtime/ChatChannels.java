package kg.kudaibergen.chat.realtime;

/**
 * Каналы Centrifugo.
 * <ul>
 *   <li>{@code chat:{chatId}} — события открытого чата (MESSAGE, READ, TYPING). Подписка — только по
 *   subscription-токену, который бэкенд выдаёт участнику чата: состав бокса меняется, поэтому список
 *   userId в имени канала не годится.</li>
 *   <li>{@code inbox:{userId}#{userId}} — личный канал: строки списка чатов (16) и счётчик непрочитанных.
 *   Centrifugo сам пускает туда только этого пользователя (user-limited channel). Presence на нём =
 *   «в сети».</li>
 * </ul>
 */
public final class ChatChannels {

   private ChatChannels() {
   }

   public static String chat(Long chatId) {
      return "chat:" + chatId;
   }

   public static String inbox(Long userId) {
      return "inbox:" + userId + "#" + userId;
   }

   /** id чата из имени канала chat:{id}; null — это не канал чата. */
   public static Long chatIdOf(String channel) {
      if (channel == null || !channel.startsWith("chat:")) {
         return null;
      }
      try {
         return Long.valueOf(channel.substring("chat:".length()));
      } catch (NumberFormatException e) {
         return null;
      }
   }
}
