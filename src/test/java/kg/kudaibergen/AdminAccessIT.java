package kg.kudaibergen;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Вход в админку, права, журнал, маскирование телефонов, поиск (фаза 1). */
class AdminAccessIT extends AbstractIntegrationTest {

   @Test
   void входПарольSmsCookieИВыход() throws Exception {
      MvcResult verified = adminLogin("+996700500501", "MARKET_ADMIN");
      JsonNode session = json.readTree(verified.getResponse().getContentAsString(StandardCharsets.UTF_8));
      assertThat(session.get("expiresIn").asLong()).isEqualTo(900);
      assertThat(session.get("me").get("role").asText()).isEqualTo("MARKET_ADMIN");
      assertThat(session.get("me").get("permissions").toString()).contains("PII_VIEW").doesNotContain("STAFF_MANAGE");
      assertThat(session.has("refreshToken")).isFalse();

      Cookie refresh = verified.getResponse().getCookie("admin_refresh");
      assertThat(refresh).isNotNull();
      assertThat(refresh.isHttpOnly()).isTrue();
      assertThat(refresh.getPath()).isEqualTo("/api/v1/admin/auth");

      MvcResult refreshed = mvc.perform(post("/api/v1/admin/auth/refresh").cookie(refresh)).andReturn();
      assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
      // старый refresh погашен ротацией
      assertThat(mvc.perform(post("/api/v1/admin/auth/refresh").cookie(refresh)).andReturn()
            .getResponse().getStatus()).isEqualTo(401);

      Cookie rotated = refreshed.getResponse().getCookie("admin_refresh");
      mvc.perform(post("/api/v1/admin/auth/logout").cookie(rotated));
      assertThat(mvc.perform(post("/api/v1/admin/auth/refresh").cookie(rotated)).andReturn()
            .getResponse().getStatus()).isEqualTo(401);
   }

   @Test
   void неверныйПарольИНеСотрудникНеРазличаются() throws Exception {
      adminLogin("+996700500502", "MARKET_ADMIN");
      JsonNode wrong = call(jsonPost("/api/v1/admin/auth/login",
            "{\"phone\":\"+996700500502\",\"password\":\"wrong-pass-1\"}"), 401);
      login("+996700500503", "BUYER");
      JsonNode stranger = call(jsonPost("/api/v1/admin/auth/login",
            "{\"phone\":\"+996700500503\",\"password\":\"" + ADMIN_PASSWORD + "\"}"), 401);
      assertThat(wrong.get("code").asText()).isEqualTo("ADMIN_BAD_CREDENTIALS");
      assertThat(stranger.get("code").asText()).isEqualTo("ADMIN_BAD_CREDENTIALS");
   }

   @Test
   void токеныПриложенияИАдминкиНеПодменяютДругДруга() throws Exception {
      String app = accessToken("+996700500504", "BUYER");
      JsonNode forbidden = call(authed(get("/api/v1/admin/me"), app), 403);
      assertThat(forbidden.get("code").asText()).isEqualTo("FORBIDDEN");

      String admin = adminToken("+996700500505", "MARKET_ADMIN");
      call(authed(get("/api/v1/me"), admin), 401);
      call(authed(get("/api/v1/admin/me"), admin), 200);
   }

   @Test
   void безПраваВсегда403ИОтключениеДействуетСразу() throws Exception {
      String marketAdmin = adminToken("+996700500506", "MARKET_ADMIN");
      String superAdmin = adminToken("+996700500507", "SUPER_ADMIN");

      adminJdbc.update("delete from admin_role_permissions where admin_role = 'MARKET_ADMIN' and permission = 'SELLERS_VIEW'");
      try {
         call(authed(get("/api/v1/admin/shops"), marketAdmin), 403);
         call(authed(get("/api/v1/admin/shops"), superAdmin), 200);
         JsonNode me = call(authed(get("/api/v1/admin/me"), marketAdmin), 200);
         assertThat(me.get("badges").get("shopsPending").isNull()).isTrue();
      } finally {
         adminJdbc.update("insert into admin_role_permissions values ('MARKET_ADMIN', 'SELLERS_VIEW') on conflict do nothing");
      }

      adminJdbc.update("update admin_members set is_active = false where user_id = (select id from users where phone = ?)",
            "+996700500506");
      // сессия отключённого сотрудника больше не принимается — как без токена
      call(authed(get("/api/v1/admin/me"), marketAdmin), 401);
   }

   @Test
   void действиеПишетсяВЖурналСКомментарием() throws Exception {
      String admin = adminToken("+996700500508", "MARKET_ADMIN");
      long rowId = adminJdbc.queryForObject("select id from market_rows order by sort_order limit 1", Long.class);
      long containerId = adminJdbc.queryForObject(
            "select id from containers where row_id = ? order by id limit 1", Long.class, rowId);

      call(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .patch("/api/v1/admin/market/containers/" + containerId)
            .contentType(MediaType.APPLICATION_JSON).content("{\"tenantPhone\":\"+996555000222\"}"), admin), 200);

      var row = adminJdbc.queryForMap("""
            select a.action, a.entity_type, a.entity_id, a.after::text as after from admin_audit_log a
            join users u on u.id = a.admin_id where u.phone = ? and a.action = 'CONTAINER_UPDATE'""", "+996700500508");
      assertThat(row.get("entity_type")).isEqualTo("CONTAINER");
      assertThat(((Number) row.get("entity_id")).longValue()).isEqualTo(containerId);
      assertThat(row.get("after").toString()).contains("+996555000222");
   }

   @Test
   void поискПоТелефонуИКонтейнеру() throws Exception {
      String admin = adminToken("+996700500509", "SUPER_ADMIN");
      login("+996700500510", "BUYER");

      JsonNode byPhone = call(authed(get("/api/v1/admin/search").param("q", "500510"), admin), 200);
      assertThat(byPhone.get("users").findValuesAsText("phone")).contains("+996700500510");

      JsonNode row = call(authed(get("/api/v1/admin/search").param("q", "Ряд 14 · 1"), admin), 200);
      assertThat(row.get("containers").isArray()).isTrue();
   }
}
