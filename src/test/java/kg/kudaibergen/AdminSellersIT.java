package kg.kudaibergen;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Админка, фаза 2: продавцы [A2] — отказ, споры, импорт арендаторов; мастера [A7]. */
class AdminSellersIT extends AbstractIntegrationTest {

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void отказОсвобождаетКонтейнерИМагазинМожетВыбратьДругой() throws Exception {
      String admin = adminToken("+996700900001", "MARKET_ADMIN");
      String owner = accessToken("+996700900002", "SELLER");
      long container = containerId("18", "SOUTH", 10);
      long shopId = call(authed(jsonPost("/api/v1/shops", createBody(container, "Мир Фильтров")), owner), 201)
            .get("id").asLong();

      JsonNode pending = call(authed(get("/api/v1/admin/shops").param("tab", "PENDING").param("q", "Мир Фильтров"),
            admin), 200);
      assertThat(pending.get("items").get(0).get("tenantMatch").asText()).isEqualTo("AWAITING_CHECK");

      JsonNode rejected = call(authed(jsonPost("/api/v1/admin/shops/" + shopId + "/reject",
            "{\"reason\":\"Нет в списке арендаторов\"}"), admin), 200);
      assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
      assertThat(rejected.get("statusReason").asText()).isEqualTo("Нет в списке арендаторов");
      assertThat(call(authed(get("/api/v1/my/shop"), owner), 200).get("status").asText()).isEqualTo("REJECTED");

      // место свободно: на нём может зарегистрироваться настоящий арендатор
      String tenant = accessToken("+996700900003", "SELLER");
      call(authed(jsonPost("/api/v1/shops", createBody(container, "Настоящий бокс")), tenant), 201);

      // отклонённый выбирает другой контейнер — снова на проверке
      call(authed(jsonPost("/api/v1/my/shop/relocation", "{\"containerId\":" + containerId("18", "SOUTH", 11) + "}"),
            owner), 200);
      assertThat(call(authed(get("/api/v1/admin/shops/" + shopId), admin), 200).get("status").asText())
            .isEqualTo("PENDING_VERIFICATION");
      assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where action = 'SHOP_REJECT' "
            + "and entity_id = ?", Long.class, shopId)).isEqualTo(1);
   }

   @Test
   void спорРешёнВПользуЗаявителя() throws Exception {
      String admin = adminToken("+996700900011", "MARKET_ADMIN");
      String squatter = accessToken("+996700900012", "SELLER");
      long container = containerId("18", "SOUTH", 12);
      long squatterShop = call(authed(jsonPost("/api/v1/shops", createBody(container, "Захватчик")), squatter), 201)
            .get("id").asLong();

      String claimant = accessToken("+996700900013", "SELLER");
      JsonNode claim = call(authed(jsonPost("/api/v1/market/containers/" + container + "/claim",
            "{\"text\":\"Арендую с 2019 года\"}"), claimant), 201);
      long disputeId = claim.get("id").asLong();

      JsonNode tab = call(authed(get("/api/v1/admin/shops").param("tab", "DISPUTES"), admin), 200);
      assertThat(tab.get("items").findValuesAsText("name")).contains("Захватчик");
      assertThat(call(authed(get("/api/v1/admin/me"), admin), 200).get("badges").get("disputesOpen").asLong())
            .isPositive();

      JsonNode resolved = call(authed(jsonPost("/api/v1/admin/disputes/" + disputeId + "/resolve",
            "{\"winner\":\"CLAIMANT\",\"comment\":\"По договору аренды\"}"), admin), 200);
      assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
      assertThat(call(authed(get("/api/v1/admin/shops/" + squatterShop), admin), 200).get("status").asText())
            .isEqualTo("REJECTED");
      assertThat(jdbc.queryForObject("select tenant_phone from containers where id = ?", String.class, container))
            .isEqualTo("+996700900013");
      call(authed(jsonPost("/api/v1/shops", createBody(container, "Законный бокс")), claimant), 201);
   }

   @Test
   void импортАрендаторовПредпросмотрИПрименение() throws Exception {
      String admin = adminToken("+996700900021", "MARKET_ADMIN");
      String csv = "Ряд;Номер;Сторона;ФИО;Телефон\n"
            + "18;13;Ю;Токтосунов Азамат;0555 12 34 56\n"
            + "18;999;Ю;Нет такого;0555000000\n"
            + "Ряд 18;14;юг;;+996 700 90 00 22\n";
      JsonNode preview = mvc.perform(authed(multipart("/api/v1/admin/tenants/import")
                  .file(new MockMultipartFile("file", "tenants.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))),
            admin)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).transform(this::tree);
      assertThat(preview.get("rowsOk").asInt()).isEqualTo(2);
      assertThat(preview.get("rowsError").asInt()).isEqualTo(1);
      assertThat(preview.get("errors").get(0).get("line").asInt()).isEqualTo(3);
      long container13 = containerId("18", "SOUTH", 13);
      assertThat(jdbc.queryForObject("select tenant_name from containers where id = ?", String.class, container13))
            .isNull();

      call(authed(jsonPost("/api/v1/admin/tenants/import/" + preview.get("id").asLong() + "/apply", "{}"), admin), 200);
      assertThat(jdbc.queryForMap("select tenant_name, tenant_phone from containers where id = ?", container13))
            .containsEntry("tenant_name", "Токтосунов Азамат").containsEntry("tenant_phone", "+996555123456");
      call(authed(jsonPost("/api/v1/admin/tenants/import/" + preview.get("id").asLong() + "/apply", "{}"), admin), 409);
   }

   @Test
   void мастерВручнуюБлокировкаИСписок() throws Exception {
      String admin = adminToken("+996700900031", "MARKET_ADMIN");
      JsonNode created = call(authed(jsonPost("/api/v1/admin/masters", """
            {"ownerPhone":"+996700900032","ownerName":"Эрлан",
             "profile":{"name":"СТО Ходовик","services":["CAR_REPAIR"],"allBrands":true,
                        "address":"ул. Садыгалиева 41","lat":42.85,"lng":74.62,"radiusKm":5}}"""), admin), 201);
      long masterId = created.get("id").asLong();
      assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
      assertThat(created.get("check").asText()).isEqualTo("NO_PHOTOS");

      JsonNode list = call(authed(get("/api/v1/admin/masters").param("service", "CAR_REPAIR").param("q", "Ходовик"),
            admin), 200);
      assertThat(list.get("items").findValuesAsText("name")).contains("СТО Ходовик");

      call(authed(jsonPost("/api/v1/admin/masters/" + masterId + "/block", "{\"reason\":\"Жалобы клиентов\"}"),
            admin), 200);
      JsonNode blocked = call(authed(get("/api/v1/admin/masters").param("tab", "BLOCKED"), admin), 200);
      assertThat(blocked.get("items").findValuesAsText("name")).contains("СТО Ходовик");
      JsonNode warned = call(authed(jsonPost("/api/v1/admin/masters/" + masterId + "/warn",
            "{\"reason\":\"Последнее предупреждение\"}"), admin), 200);
      assertThat(warned.get("warned").asBoolean()).isTrue();
      assertThat(warned.get("sanctions")).hasSize(2);
   }

   private JsonNode tree(String body) {
      try {
         return json.readTree(body);
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private String createBody(long containerId, String name) {
      long brand = jdbc.queryForObject("select id from brands where slug = 'toyota'", Long.class);
      long category = jdbc.queryForObject("select id from categories where slug = 'suspension'", Long.class);
      return "{\"containerId\":" + containerId + ",\"name\":\"" + name + "\",\"brandIds\":[" + brand
            + "],\"categoryIds\":[" + category + "],\"openFrom\":\"08:00\",\"openTo\":\"17:00\"}";
   }

   private long containerId(String rowCode, String side, int number) {
      return jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = ? and c.number = ?""", Long.class, rowCode, side, number);
   }
}
