package kg.kudaibergen.chat.realtime;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpServer;
import kg.kudaibergen.common.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/** События Centrifugo — тем же JSON, что и REST: даты строками ISO, не числами и не массивами. */
class CentrifugoClientTest {

   private final List<String> bodies = new CopyOnWriteArrayList<>();
   private HttpServer server;
   private CentrifugoClient client;

   record Event(Instant createdAt, LocalTime openFrom, LocalDate day) {
   }

   @BeforeEach
   void start() throws Exception {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
         bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
         exchange.sendResponseHeaders(200, -1);
         exchange.close();
      });
      server.start();
      // как настраивает Spring Boot: JavaTimeModule и даты не числами
      ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
      AppProperties properties = new AppProperties(null, null, null, null, null, null,
            new AppProperties.Centrifugo("http://localhost:" + server.getAddress().getPort(), "key",
                  "secret-secret-secret-secret-secret-32", Duration.ofHours(1)), null);
      client = new CentrifugoClient(properties, mapper);
   }

   @AfterEach
   void stop() {
      server.stop(0);
   }

   @Test
   void датыВСобытияхСтрокамиIso() {
      Event event = new Event(Instant.parse("2026-10-01T05:00:00Z"), LocalTime.of(9, 0), LocalDate.of(2026, 10, 1));

      client.publish("chat:1", event);
      client.publishAll(List.of(new CentrifugoClient.Publication("inbox:2", event)));

      assertThat(bodies).hasSize(2).allSatisfy(body -> assertThat(body)
            .contains("\"createdAt\":\"2026-10-01T05:00:00Z\"", "\"openFrom\":\"09:00:00\"", "\"day\":\"2026-10-01\"")
            .doesNotContain("1790830800"));
   }
}
