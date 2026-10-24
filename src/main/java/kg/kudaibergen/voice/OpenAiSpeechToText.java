package kg.kudaibergen.voice;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Распознавание через API OpenAI /v1/audio/transcriptions: OpenAI Whisper или совместимый сервис
 * (app.voice.openai.url). Язык не задаётся — распознаватель сам различает русский и кыргызский.
 */
@Component
@ConditionalOnProperty(name = "app.voice.provider", havingValue = "openai")
public class OpenAiSpeechToText implements SpeechToText {

   private static final Logger log = LoggerFactory.getLogger(OpenAiSpeechToText.class);

   private final RestClient rest;
   private final VoiceProperties.OpenAi config;

   public OpenAiSpeechToText(VoiceProperties properties) {
      this.config = properties.openai();
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(config.timeout());
      factory.setReadTimeout(config.timeout());
      RestClient.Builder builder = RestClient.builder().baseUrl(config.url()).requestFactory(factory);
      if (config.apiKey() != null && !config.apiKey().isBlank()) {
         builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
      }
      this.rest = builder.build();
   }

   @Override
   public String transcribe(byte[] audio, String mimeType, String fileName) {
      MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
      form.add("file", new ByteArrayResource(audio) {
         @Override
         public String getFilename() {
            return fileName;
         }
      });
      form.add("model", config.model());
      form.add("response_format", "json");
      if (config.prompt() != null && !config.prompt().isBlank()) {
         form.add("prompt", config.prompt());
      }
      try {
         JsonNode response = rest.post().uri("/v1/audio/transcriptions").contentType(MediaType.MULTIPART_FORM_DATA)
               .body(form).retrieve().body(JsonNode.class);
         String text = response == null ? "" : response.path("text").asText("").trim();
         return text.isEmpty() ? null : text;
      } catch (RestClientException ex) {
         log.warn("Распознавание голосового недоступно: {}", ex.getMessage());
         return null;
      }
   }
}
