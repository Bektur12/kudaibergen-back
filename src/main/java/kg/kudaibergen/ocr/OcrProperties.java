package kg.kudaibergen.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Распознавание текста на фото (второй этап ТЗ: номер детали с камеры 25 и строки поиска 27).
 * provider: none — выключено (503 OCR_UNAVAILABLE), google — Google Cloud Vision по API-ключу.
 * perUserHourly — лимит распознаваний на пользователя в час (платный сервис).
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(String provider, Google google, int perUserHourly) {

   public record Google(String apiKey, String url) {
   }
}
