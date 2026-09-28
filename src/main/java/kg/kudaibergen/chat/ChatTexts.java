package kg.kudaibergen.chat;

import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.entity.QuickReply;
import kg.kudaibergen.user.entity.Lang;

/** Тексты пушей чата на языке получателя (ТЗ 11.3: «имя + текст сообщения»). */
final class ChatTexts {

   private ChatTexts() {
   }

   static String buyer(Lang lang) {
      return lang == Lang.KG ? "Сатып алуучу" : "Покупатель";
   }

   /** Тело пуша: текст сообщения, для вложений — значок и подпись. */
   static String preview(Message message, Lang lang) {
      boolean kg = lang == Lang.KG;
      return switch (message.getType()) {
         case PHOTO -> withCaption(kg ? "📷 Сүрөт" : "📷 Фото", message.getText());
         case VOICE -> kg ? "🎤 Үн билдирүү" : "🎤 Голосовое сообщение";
         case VIDEO -> withCaption("🎥 Видео", message.getText());
         case QUICK -> QuickReply.ARRIVED.name().equals(message.getCode())
               ? QuickReply.ARRIVED.label(lang) : message.getText();
         default -> message.getText();
      };
   }

   private static String withCaption(String icon, String caption) {
      return caption == null || caption.isBlank() ? icon : icon + " " + caption;
   }
}
