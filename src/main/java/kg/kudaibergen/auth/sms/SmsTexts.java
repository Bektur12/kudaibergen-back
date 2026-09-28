package kg.kudaibergen.auth.sms;

import kg.kudaibergen.user.entity.Lang;

/** Тексты SMS на языке пользователя. */
public final class SmsTexts {

   private SmsTexts() {
   }

   public static String loginCode(String code, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген: код входа " + code + ". Никому его не сообщайте.";
         case KG -> "Кудайберген: кирүү коду " + code + ". Аны эч кимге айтпаңыз.";
      };
   }

   public static String deletionCode(String code, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген: код для удаления аккаунта " + code + ". Если это не вы — проигнорируйте SMS.";
         case KG -> "Кудайберген: аккаунтту өчүрүү коду " + code + ". Бул сиз болбосоңуз, SMSке көңүл бурбаңыз.";
      };
   }
}
