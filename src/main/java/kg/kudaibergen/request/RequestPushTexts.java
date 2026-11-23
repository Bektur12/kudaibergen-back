package kg.kudaibergen.request;

import kg.kudaibergen.user.entity.Lang;

/**
 * Пуш продавцу о новом запросе: сразу понятно, что это заявка покупателя, а не просто «Camry 50 · фара».
 * «Новый запрос · Camry 50 · 2012» / «Покупатель ищет: фара левая».
 */
final class RequestPushTexts {

   private RequestPushTexts() {
   }

   static String newRequestTitle(String carLabel, Lang lang) {
      return (lang == Lang.KG ? "Жаңы суроо · " : "Новый запрос · ") + carLabel;
   }

   /** Без текста (только фото или голос) — что посмотреть или послушать. */
   static String newRequestBody(String text, boolean hasPhoto, boolean hasVoice, Lang lang) {
      boolean kg = lang == Lang.KG;
      if (text != null && !text.isBlank()) {
         return (kg ? "Сатып алуучу издейт: " : "Покупатель ищет: ") + text.strip();
      }
      if (hasPhoto) {
         return kg ? "Сатып алуучу запчасть издейт — сүрөттү караңыз" : "Покупатель ищет запчасть — посмотрите фото";
      }
      if (hasVoice) {
         return kg ? "Сатып алуучу запчасть издейт — үн билдирүүнү угуңуз"
               : "Покупатель ищет запчасть — послушайте голосовое";
      }
      return kg ? "Сатып алуучу запчасть издейт" : "Покупатель ищет запчасть";
   }
}
