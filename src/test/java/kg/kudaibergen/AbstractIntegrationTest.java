package kg.kudaibergen;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Контейнеры поднимаются один раз на весь прогон (singleton), контекст Spring кэшируется
 * между классами. Kafka и MinIO добавятся вместе со своими модулями.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
   static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

   static {
      POSTGRES.start();
      REDIS.start();
   }

   @Autowired
   protected MockMvc mvc;

   @Autowired
   protected ObjectMapper json;

   @Autowired
   protected StringRedisTemplate redis;

   @DynamicPropertySource
   static void containers(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
      registry.add("spring.data.redis.host", REDIS::getHost);
      registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
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

   protected MockHttpServletRequestBuilder jsonPut(String url, String body) {
      return put(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
   }

   /** Каждый вход — с уникального «IP», чтобы тесты не упирались в лимит отправки кодов на IP. */
   protected MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String ip) {
      return request.with(r -> {
         r.setRemoteAddr(ip);
         return r;
      });
   }

   /** Вход по OTP (+ выбор роли для нового пользователя). Пауза до повторной отправки сбрасывается. Возвращает ответ verify. */
   protected JsonNode login(String phone, String role) throws Exception {
      redis.delete("otp:cooldown:login:" + phone);
      JsonNode sent = call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"),
            "10.0." + phone.substring(10, 11) + "." + phone.substring(11)), 200);
      JsonNode tokens = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + sent.get("debugCode").asText() + "\"}"), 200);
      if (tokens.get("isNewUser").asBoolean()) {
         call(authed(jsonPut("/api/v1/me/role", "{\"role\":\"" + role + "\"}"),
               tokens.get("accessToken").asText()), 200);
      }
      return tokens;
   }

   protected String accessToken(String phone, String role) throws Exception {
      return login(phone, role).get("accessToken").asText();
   }
}
