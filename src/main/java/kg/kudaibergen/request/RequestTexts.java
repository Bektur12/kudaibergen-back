package kg.kudaibergen.request;

import kg.kudaibergen.user.entity.Lang;

/** Тексты пушей модуля запросов на языке получателя (ТЗ 11.3). */
final class RequestTexts {

   private RequestTexts() {
   }

   /** «Автодеталь Азамат: есть». */
   static String haveTitle(String shopName, Lang lang) {
      return shopName + (lang == Lang.KG ? ": бар" : ": есть");
   }

   /** «Ряд 14 · конт. 12 — 3 200 сом». */
   static String haveBody(String rowLabel, int boxNumber, Integer price) {
      return haveBody(rowLabel + " · конт. " + boxNumber, price);
   }

   /** place — «Ряд 14 · конт. 12» или адрес магазина вне рынка. */
   static String haveBody(String place, Integer price) {
      return price == null ? place : place + " — " + price(price) + " сом";
   }

   /** 3200 → «3 200» (узкий неразрывный пробел, как в макете). */
   static String price(int price) {
      String digits = Integer.toString(price);
      StringBuilder grouped = new StringBuilder();
      for (int i = 0; i < digits.length(); i++) {
         if (i > 0 && (digits.length() - i) % 3 == 0) {
            grouped.append('\u202F');
         }
         grouped.append(digits.charAt(i));
      }
      return grouped.toString();
   }

   static String noReplyTitle(Lang lang) {
      return lang == Lang.KG ? "Азырынча эч ким жооп берген жок" : "Пока никто не ответил";
   }

   static String noReplyBody(String text, Lang lang) {
      return lang == Lang.KG ? "«" + text + "» — бүт базарга жибересизби?" : "«" + text + "» — отправить всему рынку?";
   }

   /** «Время вышло: 3 ответа». */
   static String expiredTitle(int have, Lang lang) {
      if (lang == Lang.KG) {
         return "Убакыт бүттү: " + have + " жооп";
      }
      return "Время вышло: " + have + " " + answers(have);
   }

   static String expiredBody(String text, Lang lang) {
      return lang == Lang.KG ? "«" + text + "» — узартасызбы?" : "«" + text + "» — продлить?";
   }

   /** 1 ответ, 2 ответа, 5 ответов, 11 ответов, 21 ответ. */
   static String answers(int count) {
      int mod100 = count % 100;
      int mod10 = count % 10;
      if (mod100 >= 11 && mod100 <= 14) {
         return "ответов";
      }
      if (mod10 == 1) {
         return "ответ";
      }
      return mod10 >= 2 && mod10 <= 4 ? "ответа" : "ответов";
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
