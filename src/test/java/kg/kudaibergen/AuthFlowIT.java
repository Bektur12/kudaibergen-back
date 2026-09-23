package kg.kudaibergen;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AuthFlowIT extends AbstractIntegrationTest {

   @Test
   void входПоСмсКодуСозданиеПользователяИВыборРоли() throws Exception {
      String phone = "+996700100101";

      JsonNode code = call(jsonPost("/api/v1/auth/request-code", "{\"phone\":\"" + phone + "\"}"), 200);
      assertThat(code.get("expiresInSeconds").asLong()).isEqualTo(120);
      assertThat(code.get("debugCode").asText()).hasSize(4);

      JsonNode wrong = call(jsonPost("/api/v1/auth/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"0000\"}"), 400);
      assertThat(wrong.get("code").asText()).isIn("INVALID_CODE", "CODE_ALREADY_USED");

      JsonNode tokens = call(jsonPost("/api/v1/auth/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + code.get("debugCode").asText() + "\"}"), 200);
      assertThat(tokens.get("isNewUser").asBoolean()).isTrue();

      String access = tokens.get("accessToken").asText();
      JsonNode registered = call(authed(jsonPost("/api/v1/auth/register-role",
            "{\"role\":\"BUYER\",\"name\":\"Азамат\"}"), access), 200);
      access = registered.get("accessToken").asText();

      JsonNode me = call(authed(get("/api/v1/me"), access), 200);
      assertThat(me.get("role").asText()).isEqualTo("BUYER");
      assertThat(me.get("name").asText()).isEqualTo("Азамат");
      assertThat(me.get("city").asText()).isEqualTo("Бишкек");

      // повторный выбор роли запрещён
      JsonNode conflict = call(authed(jsonPost("/api/v1/auth/register-role",
            "{\"role\":\"SELLER\",\"name\":\"Азамат\"}"), access), 409);
      assertThat(conflict.get("code").asText()).isEqualTo("REGISTRATION_ALREADY_COMPLETED");
   }

   @Test
   void повторныйЗапросКодаБыстрееМинутыОтклоняется() throws Exception {
      String phone = "+996700100102";
      call(jsonPost("/api/v1/auth/request-code", "{\"phone\":\"" + phone + "\"}"), 200);

      JsonNode tooOften = call(jsonPost("/api/v1/auth/request-code", "{\"phone\":\"" + phone + "\"}"), 429);
      assertThat(tooOften.get("code").asText()).isEqualTo("SMS_TOO_OFTEN");
   }

   @Test
   void безТокенаДоступЗапрещён() throws Exception {
      JsonNode error = call(get("/api/v1/me"), 401);
      assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");
   }
}
