package kg.kudaibergen.chat.realtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Клиент HTTP Server API Centrifugo (https://centrifugal.dev/docs/server/server_api) — им бэкенд
 * доставляет уже сохранённые события подписчикам канала и узнаёт, кто сейчас на связи.
 * Сам обмен сообщениями между клиентами (WS/SockJS) идёт напрямую в Centrifugo, бэкенд в этом
 * не участвует — вся бизнес-логика и валидация остаются в HTTP-эндпоинтах, как и раньше.
 *
 * Centrifugo может быть недоступен без вреда для чата: REST + outbox-пуш — источник истины,
 * live-доставка через Centrifugo — только ускоряющая надстройка. Поэтому любая ошибка здесь
 * гасится (publish — молча, presence — как "никого нет", это безопасный дефолт: тогда просто
 * уйдёт пуш-уведомление вместо живой доставки).
 */
@Component
public class CentrifugoClient {

   private static final Logger log = LoggerFactory.getLogger(CentrifugoClient.class);

   private final RestClient restClient;

   public CentrifugoClient(AppProperties properties) {
      AppProperties.Centrifugo config = properties.centrifugo();
      this.restClient = RestClient.builder()
            .baseUrl(config.apiUrl())
            .defaultHeader("X-API-Key", config.apiKey())
            .build();
   }

   /** Диагностика: реально ли бэкенд достучался до Centrifugo (URL/API-key верные), в отличие
    * от publish/presence — тут ошибка не гасится, а возвращается как есть, чтобы её было видно
    * через GET /realtime/diagnostics, а не только по логам. */
   public String ping() {
      try {
         String body = restClient.post().uri("/info").body(Map.of()).retrieve().body(String.class);
         return "OK: " + body;
      } catch (RestClientException ex) {
         return "FAIL: " + ex.getMessage();
      }
   }

   /** Публикация уже сохранённого события — не должна ронять бизнес-транзакцию. */
   @Async("appTaskExecutor")
   public void publish(String channel, Object data) {
      try {
         restClient.post().uri("/publish")
               .body(Map.of("channel", channel, "data", data))
               .retrieve()
               .toBodilessEntity();
      } catch (RestClientException ex) {
         log.warn("Centrifugo publish в {} не удался: {}", channel, ex.getMessage());
      }
   }

   /** userId, у кого прямо сейчас живая подписка на канал. */
   public Set<Long> presentUserIds(String channel) {
      return presentUserIdsBatch(List.of(channel)).getOrDefault(channel, Set.of());
   }

   /** То же самое одним HTTP-запросом на несколько каналов сразу (см. Server API batch) —
    * важно для списка чатов, где иначе был бы N+1 к Centrifugo на каждую строку. */
   public Map<String, Set<Long>> presentUserIdsBatch(List<String> channels) {
      if (channels.isEmpty()) {
         return Map.of();
      }
      try {
         List<Map<String, Object>> commands = channels.stream()
               .map(channel -> Map.<String, Object>of("presence", Map.of("channel", channel)))
               .toList();
         JsonNode response = restClient.post().uri("/batch")
               .body(Map.of("commands", commands))
               .retrieve()
               .body(JsonNode.class);
         return toPresenceMap(channels, response);
      } catch (RestClientException ex) {
         log.warn("Centrifugo presence не удался: {}", ex.getMessage());
         return Map.of();
      }
   }

   private Map<String, Set<Long>> toPresenceMap(List<String> channels, JsonNode response) {
      Map<String, Set<Long>> result = new LinkedHashMap<>();
      JsonNode replies = response == null ? null : response.path("replies");
      for (int i = 0; i < channels.size(); i++) {
         result.put(channels.get(i), usersOf(replies != null && i < replies.size() ? replies.get(i) : null));
      }
      return result;
   }

   private Set<Long> usersOf(JsonNode reply) {
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
               // системный/анонимный клиент без числового userId — не наш случай, пропускаем
            }
         }
      });
      return users;
   }
}
