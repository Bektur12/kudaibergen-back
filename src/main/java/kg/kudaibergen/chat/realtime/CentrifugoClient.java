package kg.kudaibergen.chat.realtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP Server API Centrifugo (https://centrifugal.dev/docs/server/server_api): публикация уже сохранённых
 * событий и presence. Источник истины — REST и база, Centrifugo только ускоряет доставку, поэтому ошибки
 * здесь гасятся: publish — молча, presence — «никого нет» (тогда уйдёт пуш вместо живой доставки).
 */
@Component
public class CentrifugoClient {

   private static final Logger log = LoggerFactory.getLogger(CentrifugoClient.class);

   private final RestClient rest;

   /**
    * События сериализуются тем же ObjectMapper, что и ответы REST: даты — ISO-строками («2026-10-01T05:00:00Z»,
    * «09:00»), а не числами и массивами, как у RestClient по умолчанию.
    */
   public CentrifugoClient(AppProperties properties, ObjectMapper objectMapper) {
      AppProperties.Centrifugo config = properties.centrifugo();
      this.rest = RestClient.builder()
            .baseUrl(config.apiUrl())
            .defaultHeader("X-API-Key", config.apiKey())
            .messageConverters(converters -> {
               converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
               converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
            })
            .build();
   }

   /** Одна публикация; вызывается после коммита (см. ChatRealtime). */
   public void publish(String channel, Object data) {
      try {
         rest.post().uri("/publish").body(Map.of("channel", channel, "data", data)).retrieve().toBodilessEntity();
      } catch (RestClientException ex) {
         log.warn("Centrifugo publish в {} не удался: {}", channel, ex.getMessage());
      }
   }

   /** Несколько публикаций одним запросом (batch), в заданном порядке: чат + личные каналы людей бокса. */
   public void publishAll(List<Publication> publications) {
      if (publications.isEmpty()) {
         return;
      }
      List<Map<String, Object>> commands = publications.stream()
            .map(publication -> Map.<String, Object>of("publish",
                  Map.of("channel", publication.channel(), "data", publication.data())))
            .toList();
      try {
         rest.post().uri("/batch").body(Map.of("commands", commands)).retrieve().toBodilessEntity();
      } catch (RestClientException ex) {
         log.warn("Centrifugo batch publish ({} событий) не удался: {}", publications.size(), ex.getMessage());
      }
   }

   public record Publication(String channel, Object data) {
   }

   /** Кто сейчас подписан на каждый из каналов (одним batch-запросом). Ошибка — пустые множества. */
   public Map<String, Set<Long>> presence(List<String> channels) {
      if (channels.isEmpty()) {
         return Map.of();
      }
      try {
         List<Map<String, Object>> commands = channels.stream()
               .map(channel -> Map.<String, Object>of("presence", Map.of("channel", channel)))
               .toList();
         JsonNode response = rest.post().uri("/batch").body(Map.of("commands", commands)).retrieve()
               .body(JsonNode.class);
         JsonNode replies = response == null ? null : response.path("replies");
         Map<String, Set<Long>> result = new LinkedHashMap<>();
         for (int i = 0; i < channels.size(); i++) {
            result.put(channels.get(i), usersOf(replies != null && i < replies.size() ? replies.get(i) : null));
         }
         return result;
      } catch (RestClientException ex) {
         log.warn("Centrifugo presence не удался: {}", ex.getMessage());
         return Map.of();
      }
   }

   private static Set<Long> usersOf(JsonNode reply) {
      Set<Long> users = new LinkedHashSet<>();
      if (reply == null) {
         return users;
      }
      reply.path("presence").path("presence").forEach(client -> {
         String userId = client.path("user").asText(null);
         if (userId != null && !userId.isBlank()) {
            try {
               users.add(Long.valueOf(userId));
            } catch (NumberFormatException ignored) {
               // анонимный клиент без числового userId — не наш
            }
         }
      });
      return users;
   }
}
