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
    * «⏰ Покупатель ждёт ответа · Camry 50» → «🔔 Вы ещё не ответили · Camry 50» → «Успейте ответить первым · Camry 50».
    */
   static String remindTitle(String carLabel, int attempt, Lang lang) {
      boolean kg = lang == Lang.KG;
      String lead = switch (attempt) {
         case 1 -> kg ? "⏰ Сатып алуучу жоопту күтүп жатат · " : "⏰ Покупатель ждёт ответа · ";
         case 2 -> kg ? "🔔 Сиз али жооп бере элексиз · " : "🔔 Вы ещё не ответили · ";
         default -> kg ? "Биринчи болуп жооп бериңиз · " : "Успейте ответить первым · ";
      };
      return lead + carLabel;
   }

   /**
    * Что ищут — и короткий призыв, каждый раз свой:
    * «Покупатель ищет: фара. Есть у вас?» → «…«Есть» или «Нет» — одно нажатие» → «Всё ещё ищут: фара. Ответ займёт секунду».
    */
   static String remindBody(String text, boolean hasPhoto, boolean hasVoice, int attempt, Lang lang) {
      boolean kg = lang == Lang.KG;
      boolean hasText = text != null && !text.isBlank();
      if (attempt >= 3) {
         String what = hasText ? (kg ? "Дагы эле издеп жатышат: " : "Всё ещё ищут: ") + text.strip()
               : newRequestBody(text, hasPhoto, hasVoice, lang);
         return what + (kg ? ". Жооп бир секунд алат" : ". Ответ займёт секунду");
      }
      String tail = attempt == 1 ? (kg ? ". Сизде барбы?" : ". Есть у вас?")
            : (kg ? ". «Бар» же «Жок» — бир басуу" : ". «Есть» или «Нет» — одно нажатие");
      return newRequestBody(text, hasPhoto, hasVoice, lang) + tail;
   }
}
