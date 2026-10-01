package kg.kudaibergen.ocr;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Cloud Vision, TEXT_DETECTION (https://cloud.google.com/vision/docs/ocr): одно фото — один запрос,
 * ответ — fullTextAnnotation.text. Ключ — app.ocr.google.api-key (переменная OCR_GOOGLE_API_KEY).
 */
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "google")
public class GoogleVisionTextRecognizer implements TextRecognizer {

   private static final Logger log = LoggerFactory.getLogger(GoogleVisionTextRecognizer.class);

   private final RestClient rest;
   private final String apiKey;

   public GoogleVisionTextRecognizer(OcrProperties properties) {
      this.rest = RestClient.builder().baseUrl(properties.google().url()).build();
      this.apiKey = properties.google().apiKey();
   }

   @Override
   public String recognize(byte[] image) {
      Map<String, Object> body = Map.of("requests", List.of(Map.of(
            "image", Map.of("content", Base64.getEncoder().encodeToString(image)),
            "features", List.of(Map.of("type", "TEXT_DETECTION")))));
      JsonNode response;
      try {
         response = rest.post().uri(uri -> uri.path("/v1/images:annotate").queryParam("key", apiKey).build())
               .body(body).retrieve().body(JsonNode.class);
      } catch (RestClientException ex) {
         log.warn("Google Vision недоступен: {}", ex.getMessage());
         throw unavailable();
      }
      JsonNode first = response == null ? null : response.path("responses").path(0);
      if (first == null || first.has("error")) {
         log.warn("Google Vision вернул ошибку: {}", first == null ? "пустой ответ" : first.path("error"));
         throw unavailable();
      }
      return first.path("fullTextAnnotation").path("text").asText("");
   }

   private static ApiException unavailable() {
      return new ApiException("OCR_UNAVAILABLE", "Не удалось распознать фото — попробуйте ещё раз или введите номер",
            HttpStatus.SERVICE_UNAVAILABLE);
   }
}
