package kg.kudaibergen;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Карта из сида V3 (market-map.json): схема, ряды, QR, маршрут до контейнера, админка. */
class MarketIT extends AbstractIntegrationTest {

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void схемаЦеликомИEtag() throws Exception {
      MvcResult result = mvc.perform(get("/api/v1/market/map")).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      String etag = result.getResponse().getHeader("ETag");
      assertThat(etag).startsWith("\"map-v");
      JsonNode map = json.readTree(result.getResponse().getContentAsByteArray());
      assertThat(map.get("rows")).hasSize(25);
      assertThat(map.get("passages")).hasSize(29);
      assertThat(map.get("entrances").get(0).get("label").asText()).isEqualTo("Вход");
      assertThat(map.get("streets").findValuesAsText("name")).contains("УЛ. БИЛИМ");
      JsonNode row14 = rowByCode(map.get("rows"), "14");
      assertThat(row14.get("containers")).hasSize(30);

      int notModified = mvc.perform(get("/api/v1/market/map").header("If-None-Match", etag))
            .andReturn().getResponse().getStatus();
      assertThat(notModified).isEqualTo(304);
   }

   @Test
   void рядыВПорядкеСеткиИКонтейнерыРяда() throws Exception {
      JsonNode rows = call(get("/api/v1/market/rows"), 200);
      List<String> codes = new ArrayList<>();
      rows.forEach(row -> codes.add(row.get("code").asText()));
      assertThat(codes.subList(0, 12)).containsExactly("18", "16", "14", "12", "10", "8", "6", "4", "1", "З", "Ю", "Я");

      long row14 = rowId("14");
      JsonNode detail = call(get("/api/v1/market/rows/" + row14), 200);
      assertThat(detail.get("sides")).hasSize(2);
      assertThat(detail.get("sides").get(0).get("side").asText()).isEqualTo("NORTH");
      JsonNode first = detail.get("sides").get(0).get("containers").get(0);
      assertThat(first.get("number").asInt()).isEqualTo(1);
      assertThat(first.get("occupied").asBoolean()).isFalse();
      assertThat(first.get("shop").isNull()).isTrue();

      JsonNode found = call(get("/api/v1/market/search").param("q", "ряд 14 12"), 200);
      assertThat(found.get("containers")).hasSize(2);
      assertThat(found.get("containers").get(0).get("rowCode").asText()).isEqualTo("14");
      assertThat(call(get("/api/v1/market/search").param("q", "Ю"), 200).get("rows").get(0).get("code").asText())
            .isEqualTo("Ю");
   }

   @Test
   void маршрутДоКонтейнераОтВходаИОтТочки() throws Exception {
      long box12 = containerId("14", "NORTH", 12);

      JsonNode fromEntrance = call(get("/api/v1/market/route").param("toContainerId", String.valueOf(box12)), 200);
      assertThat(fromEntrance.get("fromSource").asText()).isEqualTo("ENTRANCE");
      assertThat(fromEntrance.get("target").get("number").asInt()).isEqualTo(12);
      assertThat(fromEntrance.get("distanceM").asInt()).isPositive();
      assertThat(fromEntrance.get("minutes").asInt()).isPositive();
      assertThat(fromEntrance.get("steps").get(0).get("text").asText()).startsWith("Прямо по центральному проходу");

      JsonNode fromPoint = call(get("/api/v1/market/route").param("toContainerId", String.valueOf(box12))
            .param("fromX", "584").param("fromY", "540"), 200);
      assertThat(fromPoint.get("fromSource").asText()).isEqualTo("POINT");
      assertThat(fromPoint.get("steps").findValuesAsText("text")).containsExactly(
            "Прямо по центральному проходу ~20 м",
            "Направо — между рядами 16 и 14",
            "Через ~30 м бокс 12 по левую руку");
      assertThat(fromPoint.get("minutes").asInt()).isEqualTo(1);

      JsonNode kg = call(get("/api/v1/market/route").param("toContainerId", String.valueOf(box12))
            .param("fromX", "584").param("fromY", "540").header("Accept-Language", "ky"), 200);
      assertThat(kg.get("steps").get(1).get("text").asText()).isEqualTo("Оңго — 16 жана 14 катарлардын ортосуна");

      // GPS вне рынка (или карта не откалибрована) — маршрут от главного входа, а не ошибка
      JsonNode gps = call(get("/api/v1/market/route").param("toContainerId", String.valueOf(box12))
            .param("fromLat", "40.0").param("fromLon", "70.0"), 200);
      assertThat(gps.get("fromSource").asText()).isEqualTo("ENTRANCE");
   }

   @Test
   void qrКонтейнераИРяда() throws Exception {
      String containerToken = jdbc.queryForObject("""
            select c.qr_token from containers c join market_rows r on r.id = c.row_id
            where r.code = 'Ц' and c.side = 'SOUTH' and c.number = 3""", String.class);
      JsonNode container = call(get("/api/v1/market/qr/" + containerToken), 200);
      assertThat(container.get("type").asText()).isEqualTo("CONTAINER");
      assertThat(container.get("container").get("rowCode").asText()).isEqualTo("Ц");

      String rowToken = jdbc.queryForObject("select qr_token from market_rows where code = 'Я'", String.class);
      assertThat(call(get("/api/v1/market/qr/" + rowToken), 200).get("type").asText()).isEqualTo("ROW");
      assertThat(call(get("/api/v1/market/qr/unknown"), 404).get("code").asText()).isEqualTo("QR_NOT_FOUND");
   }

   @Test
   void админкаКонтейнерыQrИКалибровка() throws Exception {
      String buyer = accessToken("+996700300301", "BUYER");
      String marketAdmin = admin("+996700300302", "MARKET_ADMIN");
      String superadmin = admin("+996700300303", "SUPER_ADMIN");
      long rowSh = rowId("Ш");

      // обычному пользователю админка закрыта
      call(authed(jsonPut("/api/v1/admin/market/rows/" + rowSh + "/containers",
            "{\"counts\":{\"WEST\":10}}"), buyer), 403);

      JsonNode counts = call(authed(jsonPut("/api/v1/admin/market/rows/" + rowSh + "/containers",
            "{\"counts\":{\"WEST\":10,\"EAST\":6}}"), marketAdmin), 200);
      assertThat(counts.findValuesAsText("side").stream().filter("WEST"::equals)).hasSize(10);
      long east6 = containerId("Ш", "EAST", 6);
      assertThat(counts.toString()).contains("\"number\":7,\"active\":false");
      JsonNode wrongSide = call(authed(jsonPut("/api/v1/admin/market/rows/" + rowSh + "/containers",
            "{\"counts\":{\"NORTH\":3}}"), marketAdmin), 400);
      assertThat(wrongSide.get("code").asText()).isEqualTo("WRONG_SIDE");

      JsonNode tenant = call(authed(patch("/api/v1/admin/market/containers/" + east6)
            .contentType(MediaType.APPLICATION_JSON).content("{\"tenantPhone\":\"+996555000111\"}"), marketAdmin), 200);
      assertThat(tenant.get("tenantPhone").asText()).isEqualTo("+996555000111");

      MvcResult sheet = mvc.perform(authed(get("/api/v1/admin/market/qr/sheet").param("rowId", String.valueOf(rowSh)),
            marketAdmin)).andReturn();
      assertThat(sheet.getResponse().getContentType()).startsWith("text/html");
      assertThat(sheet.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("Ряд Ш · Бокс 6", "data:image/png;base64,");

      // калибровка — право MARKET_MAP_PUBLISH; отказ без права проверяет AdminAccessIT
      String anchors = """
            {"anchors":[
              {"lat":42.8890,"lon":74.6200,"x":80,"y":38,"label":"СЗ угол"},
              {"lat":42.8890,"lon":74.6285,"x":835,"y":35,"label":"СВ угол"},
              {"lat":42.8780,"lon":74.6320,"x":1330,"y":843,"label":"В угол"},
              {"lat":42.8770,"lon":74.6215,"x":238,"y":1375,"label":"ЮЗ угол"}]}""";
      JsonNode calibration = call(authed(jsonPut("/api/v1/admin/market/geo-anchors", anchors), superadmin), 200);
      assertThat(calibration.get("anchors")).hasSize(4);
      assertThat(calibration.get("metersPerPx").asDouble()).isPositive();

      JsonNode located = call(jsonPost("/api/v1/market/locate", "{\"lat\":42.8830,\"lon\":74.6250,\"accuracyM\":20}"), 200);
      assertThat(located.get("insideMarket").asBoolean()).isTrue();
      assertThat(located.get("radiusPx").asDouble()).isPositive();
      assertThat(call(get("/api/v1/market/map"), 200).get("geo").get("a").isNumber()).isTrue();
   }

   private String admin(String phone, String role) throws Exception {
      return adminToken(phone, role);
   }

   private long rowId(String code) {
      return jdbc.queryForObject("select id from market_rows where code = ?", Long.class, code);
   }

   private long containerId(String rowCode, String side, int number) {
      return jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = ? and c.number = ?""", Long.class, rowCode, side, number);
   }

   private static JsonNode rowByCode(JsonNode rows, String code) {
      for (JsonNode row : rows) {
         if (row.get("code").asText().equals(code)) {
            return row;
         }
      }
      throw new AssertionError("Нет ряда " + code);
   }
}
