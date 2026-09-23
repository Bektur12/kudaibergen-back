package kg.kudaibergen;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Один контейнер Postgres на все тесты: миграции прогоняет Flyway,
 * контекст Spring кэшируется между классами.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

   @Autowired
   protected MockMvc mvc;

   @Autowired
   protected ObjectMapper json;

   @BeforeAll
   static void startContainer() {
      if (!POSTGRES.isRunning()) {
         POSTGRES.start();
      }
   }

   @DynamicPropertySource
   static void datasource(DynamicPropertyRegistry registry) {
      if (!POSTGRES.isRunning()) {
         POSTGRES.start();
      }
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
   }

   // ─────────────────────── хелперы ───────────────────────

   protected JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
      MvcResult result = mvc.perform(request).andReturn();
      // MockMvc по умолчанию отдаёт тело в ISO-8859-1 — читаем байты как UTF-8
      String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
      if (result.getResponse().getStatus() != expectedStatus) {
         throw new AssertionError("Ожидался статус " + expectedStatus + ", получен "
               + result.getResponse().getStatus() + ", тело: " + body);
      }
      return body.isBlank() ? json.createObjectNode() : json.readTree(body);
   }

   protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
      return request.header("Authorization", "Bearer " + token);
   }

   protected MockHttpServletRequestBuilder jsonPost(String url, String body) {
      return post(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
   }

   /** Регистрация нового пользователя: код → verify → выбор роли. Возвращает access-токен. */
   protected String register(String phone, String role, String name) throws Exception {
      JsonNode code = call(jsonPost("/api/v1/auth/request-code", "{\"phone\":\"" + phone + "\"}"), 200);
      JsonNode tokens = call(jsonPost("/api/v1/auth/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + code.get("debugCode").asText() + "\"}"), 200);

      String access = tokens.get("accessToken").asText();
      if (tokens.get("isNewUser").asBoolean()) {
         JsonNode registered = call(authed(jsonPost("/api/v1/auth/register-role",
               "{\"role\":\"" + role + "\",\"name\":\"" + name + "\"}"), access), 200);
         access = registered.get("accessToken").asText();
      }
      return access;
   }
}
