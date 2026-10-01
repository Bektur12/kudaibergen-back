package kg.kudaibergen;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Сценарий «вход по OTP» (экраны 01–03) поверх настоящих Postgres и Redis. */
class AuthFlowIT extends AbstractIntegrationTest {

   @Test
   void входПоКодуРегистрацияИВыборРоли() throws Exception {
      String phone = "+996700100101";

      JsonNode sent = call(fromIp(jsonPost("/api/v1/auth/otp/send",
            "{\"phone\":\"" + phone + "\",\"lang\":\"KG\"}"), "10.1.0.1"), 200);
      assertThat(sent.get("expiresIn").asLong()).isEqualTo(120);
      assertThat(sent.get("resendIn").asLong()).isEqualTo(42);
      String code = sent.get("debugCode").asText();
      assertThat(code).matches("\\d{4}");
      // в Redis лежит хэш, а не сам код
      assertThat(redis.opsForHash().values("otp:login:" + phone)).doesNotContain(code);

      JsonNode wrong = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + otherThan(code) + "\"}"), 400);
      assertThat(wrong.get("code").asText()).isEqualTo("OTP_INVALID");
      assertThat(wrong.get("attemptsLeft").asInt()).isEqualTo(4);
      assertThat(wrong.get("type").asText()).isEqualTo("https://kudaibergen.kg/problems/otp-invalid");
      assertThat(wrong.get("status").asInt()).isEqualTo(400);

      JsonNode tokens = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\",\"lang\":\"KG\"}"), 200);
      assertThat(tokens.get("isNewUser").asBoolean()).isTrue();
      assertThat(tokens.get("refreshToken").asText()).isNotBlank();
      assertThat(tokens.get("user").get("lang").asText()).isEqualTo("KG");
      String access = tokens.get("accessToken").asText();

      // код одноразовый
      JsonNode reused = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"), 400);
      assertThat(reused.get("code").asText()).isEqualTo("OTP_EXPIRED");

      JsonNode seller = call(authed(jsonPut("/api/v1/me/role", "{\"role\":\"SELLER\"}"), access), 200);
      assertThat(seller.get("role").asText()).isEqualTo("SELLER");
      assertThat(seller.get("onboarded").asBoolean()).isTrue();

      JsonNode renamed = call(authed(patch("/api/v1/me").contentType(MediaType.APPLICATION_JSON)
            .characterEncoding("UTF-8").content("{\"name\":\" Бакыт \",\"lang\":\"RU\"}"), access), 200);
      assertThat(renamed.get("name").asText()).isEqualTo("Бакыт");
      assertThat(renamed.get("lang").asText()).isEqualTo("RU");

      // повторный вход — уже не новый пользователь
      JsonNode again = login(phone, "BUYER");
      assertThat(again.get("isNewUser").asBoolean()).isFalse();
      assertThat(again.get("user").get("role").asText()).isEqualTo("SELLER");
   }

   @Test
   void повторнаяОтправкаРаньше42СекундОтклоняется() throws Exception {
      String phone = "+996700100102";
      call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"), "10.1.0.2"), 200);

      MvcResult result = mvc.perform(fromIp(jsonPost("/api/v1/auth/otp/send",
            "{\"phone\":\"" + phone + "\"}"), "10.1.0.2")).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(429);
      assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
      assertThat(result.getResponse().getHeader("Retry-After")).isNotNull();
      JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
      assertThat(body.get("code").asText()).isEqualTo("OTP_COOLDOWN");
      assertThat(body.get("retryAfter").asLong()).isBetween(1L, 42L);
   }

   @Test
   void пятьОшибокБлокируютНомерНа15Минут() throws Exception {
      String phone = "+996700100103";
      JsonNode sent = call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"),
            "10.1.0.3"), 200);
      String code = sent.get("debugCode").asText();
      String wrongCode = otherThan(code);

      for (int attempt = 1; attempt <= 4; attempt++) {
         JsonNode wrong = call(jsonPost("/api/v1/auth/otp/verify",
               "{\"phone\":\"" + phone + "\",\"code\":\"" + wrongCode + "\"}"), 400);
         assertThat(wrong.get("attemptsLeft").asInt()).isEqualTo(5 - attempt);
      }
      JsonNode last = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + wrongCode + "\"}"), 429);
      assertThat(last.get("code").asText()).isEqualTo("OTP_BLOCKED");
      assertThat(last.get("retryAfter").asLong()).isEqualTo(900);

      // даже верный код больше не принимается, и новый не отправить
      JsonNode burned = call(jsonPost("/api/v1/auth/otp/verify",
            "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"), 429);
      assertThat(burned.get("code").asText()).isEqualTo("OTP_BLOCKED");
      redis.delete("otp:cooldown:login:" + phone);
      JsonNode resend = call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"),
            "10.1.0.4"), 429);
      assertThat(resend.get("code").asText()).isEqualTo("OTP_BLOCKED");
   }

   @Test
   void удалениеАккаунтаПоSmsИОтменаВходом() throws Exception {
      String phone = "+996700100109";
      JsonNode tokens = login(phone, "BUYER");
      String access = tokens.get("accessToken").asText();

      JsonNode sent = call(authed(jsonPost("/api/v1/me/deletion/otp", "{}"), access), 200);
      // код входа не подходит для удаления — у кодов разное назначение
      call(authed(jsonPost("/api/v1/me/deletion", "{\"code\":\"" + otherThan(sent.get("debugCode").asText())
            + "\"}"), access), 400);
      JsonNode deletion = call(authed(jsonPost("/api/v1/me/deletion",
            "{\"code\":\"" + sent.get("debugCode").asText() + "\"}"), access), 200);
      assertThat(deletion.get("purgeAt").asText()).isNotBlank();

      // все сессии закрыты
      call(jsonPost("/api/v1/auth/refresh",
            "{\"refreshToken\":\"" + tokens.get("refreshToken").asText() + "\"}"), 401);

      // вход в течение 30 дней отменяет удаление, аккаунт тот же
      JsonNode back = login(phone, "BUYER");
      assertThat(back.get("user").get("id").asLong()).isEqualTo(tokens.get("user").get("id").asLong());
      assertThat(back.get("isNewUser").asBoolean()).isFalse();
   }

   @Test
   void лимитОтправкиКодовНаНомер() throws Exception {
      String phone = "+996700100104";
      for (int i = 0; i < 5; i++) {
         redis.delete("otp:cooldown:login:" + phone);
         call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"), "10.1.1." + i), 200);
      }
      redis.delete("otp:cooldown:login:" + phone);
      JsonNode limited = call(fromIp(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"" + phone + "\"}"),
            "10.1.1.9"), 429);
      assertThat(limited.get("code").asText()).isEqualTo("OTP_RATE_LIMITED");
   }

   @Test
   void невалидныйНомерОтклоняетсяПоRfc7807() throws Exception {
      JsonNode invalid = call(jsonPost("/api/v1/auth/otp/send", "{\"phone\":\"0555123456\"}"), 400);
      assertThat(invalid.get("code").asText()).isEqualTo("VALIDATION_ERROR");
      assertThat(invalid.get("errors").get(0).get("field").asText()).isEqualTo("phone");
   }

   @Test
   void ротацияRefreshТокенаИЗащитаОтПовтора() throws Exception {
      JsonNode first = login("+996700100105", "BUYER");
      String refresh1 = first.get("refreshToken").asText();

      JsonNode second = call(jsonPost("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh1 + "\"}"), 200);
      String refresh2 = second.get("refreshToken").asText();
      assertThat(refresh2).isNotEqualTo(refresh1);
      call(authed(get("/api/v1/me"), second.get("accessToken").asText()), 200);

      // старый токен предъявлен повторно — гасятся все сессии, включая свежую
      JsonNode reuse = call(jsonPost("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh1 + "\"}"), 401);
      assertThat(reuse.get("code").asText()).isEqualTo("REFRESH_TOKEN_INVALID");
      call(jsonPost("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh2 + "\"}"), 401);
   }

   @Test
   void выходГаситRefreshТокен() throws Exception {
      JsonNode tokens = login("+996700100106", "BUYER");
      String access = tokens.get("accessToken").asText();
      String refresh = tokens.get("refreshToken").asText();

      call(authed(jsonPost("/api/v1/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}"), access), 204);
      call(jsonPost("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}"), 401);
   }

   @Test
   void настройкиИУстройства() throws Exception {
      String access = accessToken("+996700100107", "SELLER");

      JsonNode defaults = call(authed(get("/api/v1/me/settings"), access), 200);
      assertThat(defaults.get("newRequestSound").asBoolean()).isTrue();

      JsonNode updated = call(authed(patch("/api/v1/me/settings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"newRequestSound\":false,\"theme\":\"DARK\"}"), access), 200);
      assertThat(updated.get("newRequestSound").asBoolean()).isFalse();
      assertThat(updated.get("notifyReplies").asBoolean()).isTrue();
      assertThat(updated.get("theme").asText()).isEqualTo("DARK");

      call(authed(jsonPost("/api/v1/devices", "{\"token\":\"fcm-abc\",\"platform\":\"ANDROID\"}"), access), 204);
      // повторная регистрация идемпотентна
      call(authed(jsonPost("/api/v1/devices", "{\"token\":\"fcm-abc\",\"platform\":\"ANDROID\"}"), access), 204);
      call(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/v1/devices/fcm-abc"), access), 204);
   }

   @Test
   void безТокенаИАдминкаЗакрыты() throws Exception {
      JsonNode error = call(get("/api/v1/me"), 401);
      assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");

      String access = accessToken("+996700100108", "BUYER");
      JsonNode forbidden = call(authed(get("/api/v1/admin/shops"), access), 403);
      assertThat(forbidden.get("code").asText()).isEqualTo("FORBIDDEN");
   }

   private static String otherThan(String code) {
      return code.equals("0000") ? "1111" : "0000";
   }
}
