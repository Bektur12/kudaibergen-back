package kg.kudaibergen;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Продление, отмена, автомобили, устройства — остальная часть API покупателя. */
class RequestLifecycleIT extends AbstractIntegrationTest {

   @Test
   void продлениеИОтменаЗапроса() throws Exception {
      String buyer = register("+996700400401", "BUYER", "Марат");
      JsonNode created = call(authed(jsonPost("/api/v1/requests", """
            {"category":"WHEELS","description":"Диски R17","carText":"Nissan Note 2014","isUrgent":true}
            """).header("Idempotency-Key", UUID.randomUUID().toString()), buyer), 201);
      long requestId = created.get("id").asLong();

      // срочный запрос живёт 2 часа
      Instant expiresAt = Instant.parse(created.get("expiresAt").asText());
      assertThat(expiresAt).isBefore(Instant.now().plusSeconds(2 * 3600 + 60));

      JsonNode extended = call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{}"), buyer), 200);
      assertThat(Instant.parse(extended.get("expiresAt").asText())).isAfter(expiresAt);

      JsonNode cancelled = call(authed(jsonPost("/api/v1/requests/" + requestId + "/cancel", "{}"), buyer), 200);
      assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

      JsonNode conflict = call(authed(jsonPost("/api/v1/requests/" + requestId + "/cancel", "{}"), buyer), 409);
      assertThat(conflict.get("code").asText()).isEqualTo("REQUEST_NOT_ACTIVE");
   }

   @Test
   void чужойЗапросНеВиденИКабинетПродавцаЗакрытДляПокупателя() throws Exception {
      String owner = register("+996700400402", "BUYER", "Эркин");
      String stranger = register("+996700400403", "BUYER", "Чужой");

      JsonNode created = call(authed(jsonPost("/api/v1/requests", """
            {"category":"ENGINE","description":"Прокладка ГБЦ","carText":"Audi A6 2004"}
            """).header("Idempotency-Key", UUID.randomUUID().toString()), owner), 201);

      call(authed(get("/api/v1/requests/" + created.get("id").asLong()), stranger), 404);
      call(authed(get("/api/v1/my-store"), stranger), 403);
   }

   @Test
   void автомобилиИУстройстваПокупателя() throws Exception {
      String buyer = register("+996700400404", "BUYER", "Тилек");

      JsonNode first = call(authed(jsonPost("/api/v1/me/vehicles",
            "{\"brand\":\"Honda\",\"model\":\"Fit\",\"year\":2012}"), buyer), 201);
      assertThat(first.get("isDefault").asBoolean()).isTrue();

      JsonNode second = call(authed(jsonPost("/api/v1/me/vehicles",
            "{\"brand\":\"Lexus\",\"model\":\"RX\",\"year\":2015,\"isDefault\":true}"), buyer), 201);
      assertThat(second.get("isDefault").asBoolean()).isTrue();

      JsonNode list = call(authed(get("/api/v1/me/vehicles"), buyer), 200);
      assertThat(list).hasSize(2);
      assertThat(list.get(0).get("id").asLong()).isEqualTo(second.get("id").asLong());
      assertThat(list.get(1).get("isDefault").asBoolean()).isFalse();

      call(authed(jsonPatch("/api/v1/me/vehicles/" + first.get("id").asLong(),
            "{\"engine\":\"1.3 бензин\"}"), buyer), 200);
      call(authed(delete("/api/v1/me/vehicles/" + first.get("id").asLong()), buyer), 204);
      assertThat(call(authed(get("/api/v1/me/vehicles"), buyer), 200)).hasSize(1);

      call(authed(jsonPost("/api/v1/devices", "{\"token\":\"fcm-token-1\",\"platform\":\"IOS\"}"), buyer), 204);
      call(authed(delete("/api/v1/devices/fcm-token-1"), buyer), 204);
   }

   private MockHttpServletRequestBuilder jsonPatch(String url, String body) {
      return patch(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
   }
}
