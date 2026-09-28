package kg.kudaibergen.chat.entity;

/**
 * TEXT, PHOTO, VOICE, VIDEO — обычные сообщения; REPLY — карточка ответа «Есть» (первое сообщение чата
 * по запросу); QUICK — быстрый ответ с кодом {@link QuickReply}; SYSTEM — плашка ({@link SystemEvent});
 * PART — карточка запчасти, с которой покупатель нажал «Написать» (29).
 */
public enum MessageType {
   TEXT,
   PHOTO,
   VOICE,
   VIDEO,
   REPLY,
   QUICK,
   SYSTEM,
   PART;

   public boolean isMedia() {
      return this == PHOTO || this == VOICE || this == VIDEO;
   }
}
