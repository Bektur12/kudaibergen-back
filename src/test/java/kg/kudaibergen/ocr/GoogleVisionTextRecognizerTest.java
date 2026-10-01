package kg.kudaibergen.ocr;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import kg.kudaibergen.common.error.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Контракт с Google Vision на локальном HTTP-сервере: формат запроса и разбор ответа. */
class GoogleVisionTextRecognizerTest {

   private HttpServer server;

   @AfterEach
   void stop() {
      if (server != null) {
         server.stop(0);
      }
   }

   @Test
   void отправляетФотоИЧитаетТекст() throws IOException {
      AtomicReference<JsonNode> request = new AtomicReference<>();
      AtomicReference<String> query = new AtomicReference<>();
      start(200, """
            {"responses":[{"fullTextAnnotation":{"text":"TOYOTA\\n48510-06420\\n"}}]}""", request, query);

      String text = recognizer().recognize(new byte[]{1, 2, 3});

      assertThat(text).contains("48510-06420");
      assertThat(query.get()).isEqualTo("key=test-key");
      JsonNode first = request.get().path("requests").path(0);
      assertThat(first.path("image").path("content").asText()).isEqualTo("AQID");
      assertThat(first.path("features").path(0).path("type").asText()).isEqualTo("TEXT_DETECTION");
   }

   @Test
   void ошибкаСервисаДаёт503() throws IOException {
      start(200, """
            {"responses":[{"error":{"code":3,"message":"Bad image data"}}]}""", new AtomicReference<>(),
            new AtomicReference<>());
      assertThatThrownBy(() -> recognizer().recognize(new byte[]{1}))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getCode()).isEqualTo("OCR_UNAVAILABLE");
   }

   private GoogleVisionTextRecognizer recognizer() {
      return new GoogleVisionTextRecognizer(new OcrProperties("google",
            new OcrProperties.Google("test-key", "http://localhost:" + server.getAddress().getPort()), 30));
   }

   private void start(int status, String body, AtomicReference<JsonNode> request, AtomicReference<String> query)
         throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/v1/images:annotate", exchange -> {
         request.set(new ObjectMapper().readTree(exchange.getRequestBody()));
         query.set(exchange.getRequestURI().getQuery());
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(status, bytes.length);
         try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
         }
      });
      server.start();
   }
}
