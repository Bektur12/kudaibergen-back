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

   /**
    * Напоминание «Ответьте» — разное в 1-й, 2-й и 3-й раз, чтобы не надоедало одним и тем же:
    * «⏰ Ответьте покупателю · Camry 50» → «Покупатель ждёт ответа · Camry 50» → «Последнее напоминание · Camry 50».
    */
   static String remindTitle(String carLabel, int attempt, Lang lang) {
      boolean kg = lang == Lang.KG;
      String lead = switch (attempt) {
         case 1 -> kg ? "⏰ Сатып алуучуга жооп бериңиз · " : "⏰ Ответьте покупателю · ";
         case 2 -> kg ? "Сатып алуучу жоопту күтүп жатат · " : "Покупатель ждёт ответа · ";
         default -> kg ? "Акыркы эскертүү · " : "Последнее напоминание · ";
      };
      return lead + carLabel;
   }

   /** Что ищут — и короткий призыв: «Есть» или «Нет» прямо из уведомления. */
   static String remindBody(String text, boolean hasPhoto, boolean hasVoice, int attempt, Lang lang) {
      boolean kg = lang == Lang.KG;
      String what = newRequestBody(text, hasPhoto, hasVoice, lang);
      String tail = switch (attempt) {
         case 1 -> kg ? ". «Бар» же «Жок» басыңыз" : ". Нажмите «Есть» или «Нет»";
         case 2 -> kg ? ". Сизде барбы?" : ". У вас есть?";
         default -> kg ? ". Жооп бериңиз — сатып алуучу башкага кетпесин" : ". Ответьте, пока покупатель не ушёл к другим";
      };
      return what + tail;
   }
}
