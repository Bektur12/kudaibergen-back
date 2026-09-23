package kg.kudaibergen;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Обрыв связи не должен превращаться в два одинаковых запроса. */
class IdempotencyIT extends AbstractIntegrationTest {

   @Test
   void повторныйPostСТемЖеКлючомВозвращаетТотЖеОтвет() throws Exception {
      String buyer = register("+996700300301", "BUYER", "Бакыт");
      String key = UUID.randomUUID().toString();
      String body = """
            {"category":"OILS","description":"Масло 5W-30, 4 литра","carText":"Honda Fit 2012"}
            """;

      JsonNode first = call(authed(jsonPost("/api/v1/requests", body).header("Idempotency-Key", key), buyer), 201);
      JsonNode second = call(authed(jsonPost("/api/v1/requests", body).header("Idempotency-Key", key), buyer), 201);

      assertThat(second.get("id").asLong()).isEqualTo(first.get("id").asLong());
      assertThat(call(authed(get("/api/v1/requests/my"), buyer), 200).get("totalElements").asLong()).isEqualTo(1);
   }

   @Test
   void разныеКлючиСоздаютРазныеЗапросы() throws Exception {
      String buyer = register("+996700300302", "BUYER", "Нурлан");
      String body = """
            {"category":"LIGHTS","description":"Левая фара","carText":"Mazda Demio 2010"}
            """;

      JsonNode first = call(authed(jsonPost("/api/v1/requests", body)
            .header("Idempotency-Key", UUID.randomUUID().toString()), buyer), 201);
      JsonNode second = call(authed(jsonPost("/api/v1/requests", body)
            .header("Idempotency-Key", UUID.randomUUID().toString()), buyer), 201);

      assertThat(second.get("id").asLong()).isNotEqualTo(first.get("id").asLong());
   }
}
