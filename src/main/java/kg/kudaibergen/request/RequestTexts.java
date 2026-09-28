package kg.kudaibergen.request;

import kg.kudaibergen.user.entity.Lang;

/** Тексты пушей модуля запросов на языке получателя (ТЗ 11.3). */
final class RequestTexts {

   private RequestTexts() {
   }

   /** «Новый запрос: стойки передние» · «Toyota Camry 50 · 2012». */
   static String newRequestTitle(String text, Lang lang) {
      return (lang == Lang.KG ? "Жаңы суроо: " : "Новый запрос: ") + text;
   }

   /** «Автодеталь Азамат: есть». */
   static String haveTitle(String shopName, Lang lang) {
      return shopName + (lang == Lang.KG ? ": бар" : ": есть");
   }

   /** «Ряд 14 · Бокс 12 — «Есть KYB и оригинал»». */
   static String haveBody(String rowLabel, int boxNumber, String message) {
      String place = rowLabel + " · Бокс " + boxNumber;
      return message == null || message.isBlank() ? place : place + " — «" + message + "»";
   }

   static String noReplyTitle(Lang lang) {
      return lang == Lang.KG ? "Азырынча эч ким жооп берген жок" : "Пока никто не ответил";
   }

   static String noReplyBody(String text, Lang lang) {
      return lang == Lang.KG ? "«" + text + "» — бүт базарга жөнөтөсүзбү?" : "«" + text + "» — отправить всему рынку?";
   }

   /** «Продажа засчитана, оценка ★ 5». */
   static String saleTitle(Integer stars, Lang lang) {
      String sale = lang == Lang.KG ? "Сатуу эсептелди" : "Продажа засчитана";
      if (stars == null) {
         return sale;
      }
      return sale + (lang == Lang.KG ? ", баа ★ " : ", оценка ★ ") + stars;
   }
}
