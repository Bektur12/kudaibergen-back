package kg.kudaibergen.chat;

/**
 * Имена каналов Centrifugo. {@code #userId1,userId2} — "user-limited channel": Centrifugo сам не
 * пустит подписаться никого, кроме перечисленных userId, без прокси-эндпоинта авторизации
 * подписки на бэкенде (см. https://centrifugal.dev/docs/server/channels).
 */
final class ChatChannels {

   private ChatChannels() {
   }

   /** ownerId может быть null в вырожденном случае (магазин без владельца) — тогда канал
    * ограничен только покупателем, чтобы не падать на Math.min/max от null. */
   static String chat(Long chatId, Long buyerId, Long ownerId) {
      if (ownerId == null) {
         return "chat:" + chatId + "#" + buyerId;
      }
      long a = Math.min(buyerId, ownerId);
      long b = Math.max(buyerId, ownerId);
      return "chat:" + chatId + "#" + a + "," + b;
   }

   /** Личный канал пользователя — живой список чатов и источник его online-статуса
    * (presence на этом канале = "приложение открыто", а не "смотрит конкретный чат"). */
   static String inbox(Long userId) {
      return "inbox:" + userId + "#" + userId;
   }
}
