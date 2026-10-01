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

   public static String shopVerificationCode(String code, String container, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген: код подтверждения продавца для " + container + ": " + code
               + ". Сообщите его, только если это ваш арендатор.";
         case KG -> "Кудайберген: " + container + " үчүн сатуучуну ырастоо коду: " + code
               + ". Ижарачыңыз болсо гана айтыңыз.";
      };
   }

   public static String shopInvite(String shopName, Lang lang) {
      return switch (lang) {
         case RU -> "Вас добавили продавцом в бокс «" + shopName + "» в приложении Кудайберген. Войдите по этому номеру.";
         case KG -> "Сизди Кудайберген тиркемесинде «" + shopName + "» боксуна сатуучу кылып кошушту. Ушул номер менен кириңиз.";
      };
   }

   public static String deletionCode(String code, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген: код для удаления аккаунта " + code + ". Если это не вы — проигнорируйте SMS.";
         case KG -> "Кудайберген: аккаунтту өчүрүү коду " + code + ". Бул сиз болбосоңуз, SMSке көңүл бурбаңыз.";
      };
   }

   public static String adminLoginCode(String code, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген, админка: код входа " + code + ". Никому его не сообщайте.";
         case KG -> "Кудайберген, админка: кирүү коду " + code + ". Аны эч кимге айтпаңыз.";
      };
   }

   public static String adminPasswordCode(String code, Lang lang) {
      return switch (lang) {
         case RU -> "Кудайберген, админка: код для смены пароля " + code + ". Если это не вы — сообщите руководству.";
         case KG -> "Кудайберген, админка: сырсөздү алмаштыруу коду " + code + ". Бул сиз болбосоңуз, жетекчиликке кабарлаңыз.";
      };
   }
}
