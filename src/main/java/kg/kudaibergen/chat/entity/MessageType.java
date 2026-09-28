package kg.kudaibergen.chat.entity;

/**
 * TEXT, PHOTO, VOICE, VIDEO — обычные сообщения; REPLY — карточка ответа «Есть» (первое сообщение чата
 * по запросу); QUICK — быстрый ответ с кодом {@link QuickReply}; SYSTEM — плашка ({@link SystemEvent}).
 */
public enum MessageType {
   TEXT,
   PHOTO,
   VOICE,
   VIDEO,
   REPLY,
   QUICK,
   SYSTEM;

   public boolean isMedia() {
      return this == PHOTO || this == VOICE || this == VIDEO;
   }
}
