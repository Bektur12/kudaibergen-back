package kg.kudaibergen.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Заглушка Server API Centrifugo (см. https://centrifugal.dev/docs/server/server_api) для
 * интеграционных тестов. Реального Centrifugo и настоящего WebSocket-транспорта здесь нет —
 * доставка клиенту это ответственность самого Centrifugo, отдельного проверенного продукта.
 * Тест проверяет только контракт бэкенда с ним: правильный канал, правильный payload,
 * правильное решение push/skip по presence.
 */
public class FakeCentrifugo {

   private static final ObjectMapper JSON = new ObjectMapper();

   private final HttpServer server;
   private final BlockingQueue<Published> published = new LinkedBlockingQueue<>();
   private final Map<String, Set<Long>> presence = new ConcurrentHashMap<>();

   public FakeCentrifugo() {
      try {
         server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
      server.createContext("/api/publish", this::handlePublish);
      server.createContext("/api/batch", this::handleBatch);
   }

   public FakeCentrifugo start() {
      server.start();
      return this;
   }

   public void stop() {
      server.stop(0);
   }

   public String apiUrl() {
      return "http://localhost:" + server.getAddress().getPort() + "/api";
   }

   /** userId, которые должны считаться присутствующими на канале при следующем presence-запросе. */
   public void present(String channel, Long... userIds) {
      presence.put(channel, Set.of(userIds));
   }

   public void reset() {
      published.clear();
      presence.clear();
   }

   /** Следующее опубликованное событие в порядке прихода на сервер, либо null по таймауту. */
   public Published takePublish(Duration timeout) throws InterruptedException {
      return published.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
   }

   private void handlePublish(HttpExchange exchange) throws IOException {
      JsonNode body = JSON.readTree(exchange.getRequestBody());
      published.add(new Published(body.path("channel").asText(), body.path("data")));
      respond(exchange, Map.of("result", Map.of()));
   }

   private void handleBatch(HttpExchange exchange) throws IOException {
      JsonNode body = JSON.readTree(exchange.getRequestBody());
      List<Map<String, Object>> replies = new ArrayList<>();
      for (JsonNode command : body.path("commands")) {
         String channel = command.path("presence").path("channel").asText();
         Map<String, Object> clients = new LinkedHashMap<>();
         int i = 0;
         for (Long userId : presence.getOrDefault(channel, Set.of())) {
            String clientId = "client-" + i++;
            clients.put(clientId, Map.of("client", clientId, "user", String.valueOf(userId)));
         }
         replies.add(Map.of("presence", Map.of("presence", clients)));
      }
      respond(exchange, Map.of("replies", replies));
   }

   private void respond(HttpExchange exchange, Object body) throws IOException {
      byte[] bytes = JSON.writeValueAsBytes(body);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
         os.write(bytes);
      }
   }

   public record Published(String channel, JsonNode data) {
   }
}
