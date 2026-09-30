package kg.kudaibergen;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Бокс продавца: регистрация → проверка → виден покупателям; сотрудники; переезд; админка. */
class ShopsIT extends AbstractIntegrationTest {

   /** GPS посреди рынка: если карта уже откалибрована другим тестом, проверка QR требует геолокацию. */
   private static final String AT_MARKET = "\"lat\":42.8830,\"lon\":74.6250";

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void регистрацияПроверкаQrИВидимостьПокупателю() throws Exception {
      String owner = accessToken("+996700400401", "SELLER");
      long container = containerId("12", "NORTH", 5);
      long toyota = brandId("toyota");

      JsonNode shop = call(authed(jsonPost("/api/v1/shops", createBody(container, "Автодеталь Азамат", toyota)),
            owner), 201);
      long shopId = shop.get("id").asLong();
      assertThat(shop.get("status").asText()).isEqualTo("PENDING_VERIFICATION");
      assertThat(shop.get("myRole").asText()).isEqualTo("OWNER");
      assertThat(shop.get("location").get("rowCode").asText()).isEqualTo("12");
      assertThat(shop.get("verification").get("required").asBoolean()).isTrue();
      assertThat(shop.get("open").get("openNow").asBoolean()).isFalse();
      assertThat(call(authed(get("/api/v1/me"), owner), 200).get("role").asText()).isEqualTo("SELLER");

      // на проверке — покупателю не виден, но место уже серое
      call(get("/api/v1/shops/" + shopId), 404);
      JsonNode row = call(get("/api/v1/market/rows/" + rowId("12")), 200);
      JsonNode slot = row.get("sides").get(0).get("containers").get(4);
      assertThat(slot.get("occupied").asBoolean()).isTrue();
      assertThat(slot.get("shop").isNull()).isTrue();

      // второй продавец на то же место — CONTAINER_TAKEN → «Это мой контейнер»
      String rival = accessToken("+996700400402", "SELLER");
      JsonNode taken = call(authed(jsonPost("/api/v1/shops", createBody(container, "Korea Plus", toyota)), rival), 409);
      assertThat(taken.get("code").asText()).isEqualTo("CONTAINER_TAKEN");
      JsonNode claim = call(authed(jsonPost("/api/v1/market/containers/" + container + "/claim",
            "{\"text\":\"Это мой бокс, аренда до 2027\"}"), rival), 201);
      assertThat(claim.get("type").asText()).isEqualTo("CONTAINER_CLAIM");

      // чужая наклейка не подходит, своя — подтверждает мгновенно
      String otherToken = qrToken("12", "NORTH", 6);
      JsonNode mismatch = call(authed(jsonPost("/api/v1/my/shop/verification/qr",
            "{\"qrToken\":\"" + otherToken + "\"," + AT_MARKET + "}"), owner), 400);
      assertThat(mismatch.get("code").asText()).isEqualTo("QR_MISMATCH");
      JsonNode verified = call(authed(jsonPost("/api/v1/my/shop/verification/qr",
            "{\"qrToken\":\"" + qrToken("12", "NORTH", 5) + "\"," + AT_MARKET + "}"), owner), 200);
      assertThat(verified.get("required").asBoolean()).isFalse();
      assertThat(verified.get("lastMethod").asText()).isEqualTo("QR");

      JsonNode visible = call(get("/api/v1/shops/" + shopId), 200);
      assertThat(visible.get("name").asText()).isEqualTo("Автодеталь Азамат");
      assertThat(visible.get("brands").get(0).get("slug").asText()).isEqualTo("toyota");
      assertThat(visible.get("phone").isNull()).isTrue();
      JsonNode byBrand = call(get("/api/v1/shops").param("brandId", String.valueOf(toyota))
            .param("rowId", String.valueOf(rowId("12"))), 200);
      assertThat(byBrand.get("items").findValuesAsText("name")).contains("Автодеталь Азамат");
      JsonNode slotAfter = call(get("/api/v1/market/rows/" + rowId("12")), 200)
            .get("sides").get(0).get("containers").get(4);
      assertThat(slotAfter.get("shop").get("name").asText()).isEqualTo("Автодеталь Азамат");

      // избранное
      String buyer = accessToken("+996700400403", "BUYER");
      call(authed(put("/api/v1/shops/" + shopId + "/favorite"), buyer), 204);
      assertThat(call(authed(get("/api/v1/shops/" + shopId), buyer), 200).get("isFavorite").asBoolean()).isTrue();
      assertThat(call(authed(get("/api/v1/me/favorite-shops"), buyer), 200)).hasSize(1);
   }

   @Test
   void сотрудникиИПраваВБоксе() throws Exception {
      String owner = accessToken("+996700400411", "SELLER");
      call(authed(jsonPost("/api/v1/shops", createBody(containerId("10", "SOUTH", 3), "Мир Ремней",
            brandId("honda"))), owner), 201);

      JsonNode members = call(authed(jsonPost("/api/v1/my/shop/members", "{\"phone\":\"+996700400412\"}"), owner), 200);
      assertThat(members).hasSize(2);
      assertThat(members.get(1).get("role").asText()).isEqualTo("STAFF");

      // приглашённый входит и сразу в режиме продавца, видит бокс
      JsonNode staffLogin = login("+996700400412", "SELLER");
      String staff = staffLogin.get("accessToken").asText();
      assertThat(staffLogin.get("user").get("role").asText()).isEqualTo("SELLER");
      assertThat(call(authed(get("/api/v1/my/shop"), staff), 200).get("myRole").asText()).isEqualTo("STAFF");

      // сотрудник может закрыть бокс, но не менять марки и не приглашать
      JsonNode closed = call(authed(patch("/api/v1/my/shop/open").contentType(MediaType.APPLICATION_JSON)
            .content("{\"isOpen\":false}"), staff), 200);
      assertThat(closed.get("open").get("closedManually").asBoolean()).isTrue();
      JsonNode forbidden = call(authed(jsonPut("/api/v1/my/shop/brands",
            "{\"brandIds\":[" + brandId("kia") + "]}"), staff), 403);
      assertThat(forbidden.get("code").asText()).isEqualTo("OWNER_ONLY");
      call(authed(jsonPost("/api/v1/my/shop/members", "{\"phone\":\"+996700400419\"}"), staff), 403);

      // владелец не может пригласить того, кто уже в другом боксе
      String otherOwner = accessToken("+996700400413", "SELLER");
      call(authed(jsonPost("/api/v1/shops", createBody(containerId("10", "SOUTH", 4), "Шины Бишкек",
            brandId("kia"))), otherOwner), 201);
      JsonNode busy = call(authed(jsonPost("/api/v1/my/shop/members", "{\"phone\":\"+996700400412\"}"), otherOwner),
            409);
      assertThat(busy.get("code").asText()).isEqualTo("USER_IN_OTHER_SHOP");

      // удаление сотрудника; владельца удалить нельзя
      long staffId = members.get(1).get("userId").asLong();
      long ownerId = members.get(0).get("userId").asLong();
      call(authed(delete("/api/v1/my/shop/members/" + ownerId), owner), 400);
      assertThat(call(authed(delete("/api/v1/my/shop/members/" + staffId), owner), 200)).hasSize(1);
      call(authed(get("/api/v1/my/shop"), staff), 404);
   }

   @Test
   void переездСПроверкойПоSmsАрендатора() throws Exception {
      String owner = accessToken("+996700400421", "SELLER");
      long oldContainer = containerId("8", "NORTH", 2);
      long newContainer = containerId("8", "NORTH", 9);
      call(authed(jsonPost("/api/v1/shops", createBody(oldContainer, "Фары и оптика", brandId("lexus"))), owner), 201);
      call(authed(jsonPost("/api/v1/my/shop/verification/qr",
            "{\"qrToken\":\"" + qrToken("8", "NORTH", 2) + "\"," + AT_MARKET + "}"), owner), 200);

      JsonNode relocation = call(authed(jsonPost("/api/v1/my/shop/relocation",
            "{\"containerId\":" + newContainer + "}"), owner), 200);
      assertThat(relocation.get("target").get("number").asInt()).isEqualTo(9);
      assertThat(relocation.get("smsAvailable").asBoolean()).isFalse();
      JsonNode noSms = call(authed(jsonPost("/api/v1/my/shop/verification/sms/send", "{}"), owner), 409);
      assertThat(noSms.get("code").asText()).isEqualTo("SMS_UNAVAILABLE");

      // администрация загрузила номер арендатора — код уходит ему
      jdbc.update("update containers set tenant_phone = '+996555000921' where id = ?", newContainer);
      reloadMarket();
      JsonNode sent = call(authed(jsonPost("/api/v1/my/shop/verification/sms/send", "{}"), owner), 200);
      assertThat(sent.get("sentTo").asText()).isEqualTo("+996 555 ••• •21");
      call(authed(jsonPost("/api/v1/my/shop/verification/sms/confirm",
            "{\"code\":\"" + sent.get("debugCode").asText() + "\"}"), owner), 200);

      JsonNode moved = call(authed(get("/api/v1/my/shop"), owner), 200);
      assertThat(moved.get("location").get("containerId").asLong()).isEqualTo(newContainer);
      assertThat(moved.get("pendingLocation").isNull()).isTrue();
      // старое место освободилось
      JsonNode oldSlot = call(get("/api/v1/market/rows/" + rowId("8")), 200).get("sides").get(0).get("containers").get(1);
      assertThat(oldSlot.get("occupied").asBoolean()).isFalse();
   }

   @Test
   void админПодтверждаетИБлокирует() throws Exception {
      String owner = accessToken("+996700400431", "SELLER");
      JsonNode shop = call(authed(jsonPost("/api/v1/shops", createBody(containerId("6", "SOUTH", 7), "Радиаторы",
            brandId("nissan"))), owner), 201);
      long shopId = shop.get("id").asLong();
      JsonNode requested = call(authed(jsonPost("/api/v1/my/shop/verification/admin-request", "{}"), owner), 200);
      assertThat(requested.get("waitingForAdmin").asBoolean()).isTrue();

      String admin = admin("+996700400432");
      JsonNode queue = call(authed(get("/api/v1/admin/shops/verification-queue"), admin), 200);
      assertThat(queue.findValuesAsText("name")).contains("Радиаторы");

      JsonNode approved = call(authed(jsonPost("/api/v1/admin/shops/" + shopId + "/approve", "{}"), admin), 200);
      assertThat(approved.get("status").asText()).isEqualTo("ACTIVE");
      call(get("/api/v1/shops/" + shopId), 200);

      JsonNode blocked = call(authed(jsonPost("/api/v1/admin/shops/" + shopId + "/block",
            "{\"reason\":\"Продаёт контрафакт\"}"), admin), 200);
      assertThat(blocked.get("status").asText()).isEqualTo("BLOCKED");
      call(get("/api/v1/shops/" + shopId), 404);
      assertThat(call(authed(get("/api/v1/my/shop"), owner), 200).get("blockReason").asText())
            .isEqualTo("Продаёт контрафакт");

      call(authed(jsonPost("/api/v1/admin/shops/" + shopId + "/unblock", "{}"), admin), 200);
      call(get("/api/v1/shops/" + shopId), 200);
   }

   @Test
   void названиеСТелефономИПустыеМаркиОтклоняются() throws Exception {
      String owner = accessToken("+996700400441", "SELLER");
      long container = containerId("4", "NORTH", 1);
      JsonNode phoneInName = call(authed(jsonPost("/api/v1/shops",
            createBody(container, "Звоните 0555123456", brandId("toyota"))), owner), 400);
      assertThat(phoneInName.get("code").asText()).isEqualTo("SHOP_NAME_CONTACTS");
      JsonNode noBrands = call(authed(jsonPost("/api/v1/shops", "{\"containerId\":" + container
            + ",\"name\":\"Бокс\",\"brandIds\":[],\"categoryIds\":[1]}"), owner), 400);
      assertThat(noBrands.get("code").asText()).isEqualTo("VALIDATION_ERROR");
   }

   private String createBody(long containerId, String name, long brandId) {
      return "{\"containerId\":" + containerId + ",\"name\":\"" + name + "\",\"brandIds\":[" + brandId
            + "],\"categoryIds\":[" + categoryId("suspension") + "],\"openFrom\":\"08:00\",\"openTo\":\"17:00\"}";
   }

   private String admin(String phone) throws Exception {
      return adminToken(phone, "MARKET_ADMIN");
   }

   @Autowired
   kg.kudaibergen.market.MarketMapService market;

   private void reloadMarket() {
      market.reload();
   }

   private long rowId(String code) {
      return jdbc.queryForObject("select id from market_rows where code = ?", Long.class, code);
   }

   private long containerId(String rowCode, String side, int number) {
      return jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = ? and c.number = ?""", Long.class, rowCode, side, number);
   }

   private String qrToken(String rowCode, String side, int number) {
      return jdbc.queryForObject("""
            select c.qr_token from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = ? and c.number = ?""", String.class, rowCode, side, number);
   }

   private long brandId(String slug) {
      return jdbc.queryForObject("select id from brands where slug = ?", Long.class, slug);
   }

   private long categoryId(String slug) {
      return jdbc.queryForObject("select id from categories where slug = ?", Long.class, slug);
   }
}
