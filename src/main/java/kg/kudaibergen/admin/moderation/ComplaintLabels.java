package kg.kudaibergen.admin.moderation;

import kg.kudaibergen.complaint.ComplaintReason;
import kg.kudaibergen.complaint.ComplaintType;

/** Подписи жалоб для готового текста в ответах админки («Чат · Спам / мошенничество»). */
public final class ComplaintLabels {

   private ComplaintLabels() {
   }

   public static String title(String type, String reason) {
      String what = type(type);
      return reason == null ? what : what + " · " + reason(reason);
   }

   static String type(String code) {
      try {
         return switch (ComplaintType.valueOf(code)) {
            case CONTAINER_CLAIM -> "Контейнер";
            case SHOP -> "Магазин";
            case PART -> "Товар";
            case SHOP_PHOTO -> "Фото места";
            case REVIEW -> "Отзыв";
            case MASTER_REVIEW -> "Отзыв о мастере";
            case CHAT -> "Чат";
            case CHAT_MESSAGE -> "Сообщение";
            case MASTER -> "Мастер";
            case SERVICE_OFFER -> "Отклик мастера";
         };
      } catch (IllegalArgumentException e) {
         return code;
      }
   }

   static String reason(String code) {
      try {
         return switch (ComplaintReason.valueOf(code)) {
            case FAKE_ORIGINAL -> "Подделка под оригинал";
            case REVIEW_WITHOUT_PURCHASE -> "Отзыв без покупки";
            case SPAM_FRAUD -> "Спам / мошенничество";
            case WRONG_PLACE -> "Не то место";
            case RUDE -> "Грубость";
            case OTHER -> "Другое";
         };
      } catch (IllegalArgumentException e) {
         return code;
      }
   }
}
