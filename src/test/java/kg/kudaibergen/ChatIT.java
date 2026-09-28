package kg.kudaibergen;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.support.FakeCentrifugo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Чат: «Есть» создаёт чат с карточкой ответа → переписка, быстрые ответы и шаблоны → прочитано →
 * блокировка → закрытие запроса. Centrifugo — заглушка: проверяем каналы и конверты событий.
 * Марка Chevrolet в других тестах не используется.
 */
class ChatIT extends AbstractIntegrationTest {

   private static final Duration WAIT = Duration.ofMillis(700);

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void естьОткрываетЧатПерепискаИЗакрытие() throws Exception {
      String seller = activeShop("+996700600601", "16", 7, "Шевроле Ряд 16");
      long shopId = shopIdOf("+996700600601");
      long sellerId = userId(seller);
      String buyer = accessToken("+996700600610", "BUYER");
      long carId = lacetti(buyer);

      long requestId = call(authed(jsonPost("/api/v1/requests",
            "{\"carId\":" + carId + ",\"text\":\"Фара передняя\",\"target\":\"MARKET\"}"), buyer), 201)
            .get("id").asLong();
      JsonNode have = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"HAVE\",\"condition\":\"USED\",\"message\":\"Есть, оригинал\",\"price\":3000}"), seller), 201);
      long chatId = have.get("chatId").asLong();
      assertThat(chatId).isPositive();
      assertThat(call(authed(get("/api/v1/requests/" + requestId + "/replies"), buyer), 200).get(0).get("chatId")
            .asLong()).isEqualTo(chatId);

      // шапка и первые сообщения: плашка оплаты и карточка ответа
      JsonNode chat = call(authed(get("/api/v1/chats/" + chatId), buyer), 200);
      assertThat(chat.get("mySide").asText()).isEqualTo("BUYER");
      assertThat(chat.get("request").get("text").asText()).isEqualTo("Фара передняя");
      assertThat(chat.get("channel").asText()).isEqualTo("chat:" + chatId);
      assertThat(chat.get("canWrite").asBoolean()).isTrue();
      JsonNode history = call(authed(get("/api/v1/chats/" + chatId + "/messages"), buyer), 200).get("items");
      assertThat(history.findValuesAsText("type")).containsExactly("REPLY", "SYSTEM");
      assertThat(history.get(0).get("payload").get("price").asInt()).isEqualTo(3000);
      assertThat(history.get(1).get("code").asText()).isEqualTo("PAY_AT_BOX");

      // покупатель пишет; повтор с тем же clientId не создаёт дубль
      CENTRIFUGO.reset();
      JsonNode sent = call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"text\":\"Беру, отложите\",\"clientId\":\"c-1\"}"), buyer), 201);
      JsonNode again = call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"text\":\"Беру, отложите\",\"clientId\":\"c-1\"}"), buyer), 201);
      assertThat(again.get("id").asLong()).isEqualTo(sent.get("id").asLong());
      assertThat(sent.get("read").asBoolean()).isFalse();
      List<FakeCentrifugo.Published> published = CENTRIFUGO.drain(WAIT);
      assertThat(published).anySatisfy(p -> {
         assertThat(p.channel()).isEqualTo("chat:" + chatId);
         assertThat(p.data().get("type").asText()).isEqualTo("MESSAGE");
         assertThat(p.data().get("payload").get("text").asText()).isEqualTo("Беру, отложите");
      });
      assertThat(published).anySatisfy(p -> {
         assertThat(p.channel()).isEqualTo("inbox:" + sellerId + "#" + sellerId);
         assertThat(p.data().get("type").asText()).isEqualTo("CHAT");
         assertThat(p.data().get("payload").get("unread").asInt()).isEqualTo(1);
      });

      // у бокса: чат в списке с непрочитанным, бейдж
      JsonNode shopChats = call(authed(get("/api/v1/chats").param("as", "SHOP"), seller), 200).get("items");
      assertThat(shopChats).hasSize(1);
      assertThat(shopChats.get(0).get("subtitle").asText()).startsWith("Фара передняя · ");
      assertThat(call(authed(get("/api/v1/chats/unread"), seller), 200).get("asShop").asInt()).isEqualTo(1);

      // быстрые ответы и свои шаблоны
      call(authed(jsonPost("/api/v1/my/shop/reply-templates", "{\"text\":\"Гарантия 2 недели\"}"), seller), 201);
      JsonNode quick = call(authed(get("/api/v1/chats/" + chatId + "/quick-replies"), seller), 200);
      assertThat(quick.findValuesAsText("text")).containsExactly("Отложил для вас", "Как пройти к боксу", "Продано",
            "Гарантия 2 недели");
      JsonNode buyerQuick = call(authed(get("/api/v1/chats/" + chatId + "/quick-replies"), buyer), 200);
      assertThat(buyerQuick.findValuesAsText("code")).containsExactly("ROUTE_TO_BOX", "CLOSE_REQUEST");

      JsonNode route = call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages", "{\"quickReply\":\"ROUTE\"}"),
            seller), 201);
      assertThat(route.get("code").asText()).isEqualTo("ROUTE");
      assertThat(route.get("payload").get("location").get("rowCode").asText()).isEqualTo("16");
      JsonNode wrongSide = call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"quickReply\":\"ARRIVED\"}"), seller), 400);
      assertThat(wrongSide.get("code").asText()).isEqualTo("QUICK_REPLY_NOT_ALLOWED");

      // бокс прочитал — у покупателя галочки, бейдж бокса пуст
      call(authed(post("/api/v1/chats/" + chatId + "/read"), seller), 204);
      JsonNode mine = call(authed(get("/api/v1/chats/" + chatId + "/messages"), buyer), 200).get("items");
      // новые первыми: ROUTE продавца, затем сообщение покупателя
      assertThat(mine.get(1).get("id").asLong()).isEqualTo(sent.get("id").asLong());
      assertThat(mine.get(1).get("read").asBoolean()).isTrue();
      assertThat(call(authed(get("/api/v1/chats/unread"), seller), 200).get("asShop").asInt()).isZero();

      // чужой не видит ни чат, ни токен подписки
      String stranger = accessToken("+996700600611", "BUYER");
      call(authed(get("/api/v1/chats/" + chatId), stranger), 404);
      call(authed(get("/api/v1/chats/" + chatId + "/subscription-token"), stranger), 404);
      assertThat(call(authed(get("/api/v1/chats/" + chatId + "/subscription-token"), buyer), 200)
            .get("token").asText()).isNotBlank();

      // блокировка: писать нельзя обоим
      call(authed(put("/api/v1/chats/" + chatId + "/block"), buyer), 200);
      JsonNode blocked = call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages", "{\"text\":\"Алло\"}"), seller),
            403);
      assertThat(blocked.get("code").asText()).isEqualTo("CHAT_BLOCKED");
      call(authed(delete("/api/v1/chats/" + chatId + "/block"), buyer), 200);

      // «Купил — закрыть запрос» → плашка в чате
      call(authed(jsonPost("/api/v1/requests/" + requestId + "/close", "{\"shopId\":" + shopId + ",\"stars\":5}"),
            buyer), 200);
      JsonNode afterClose = call(authed(get("/api/v1/chats/" + chatId + "/messages"), buyer), 200).get("items");
      assertThat(afterClose.get(0).get("code").asText()).isEqualTo("REQUEST_CLOSED");
      assertThat(call(authed(get("/api/v1/chats/" + chatId), seller), 200).get("request").get("soldHere").asBoolean())
            .isTrue();
   }

   @Test
   void прямойЧатИзПрофиляМагазина() throws Exception {
      String seller = activeShop("+996700600621", "16", 8, "Шевроле Прямой");
      long shopId = shopIdOf("+996700600621");
      String buyer = accessToken("+996700600630", "BUYER");

      JsonNode opened = call(authed(jsonPost("/api/v1/chats", "{\"shopId\":" + shopId + "}"), buyer), 200);
      JsonNode reopened = call(authed(jsonPost("/api/v1/chats", "{\"shopId\":" + shopId + "}"), buyer), 200);
      assertThat(reopened.get("id").asLong()).isEqualTo(opened.get("id").asLong());
      assertThat(opened.get("request").isNull()).isTrue();

      // пустой чат бокс не видит, после первого сообщения — видит
      assertThat(call(authed(get("/api/v1/chats").param("as", "SHOP"), seller), 200).get("items")).isEmpty();
      call(authed(jsonPost("/api/v1/chats/" + opened.get("id").asLong() + "/messages",
            "{\"text\":\"Есть колодки на Lacetti?\"}"), buyer), 201);
      assertThat(call(authed(get("/api/v1/chats").param("as", "SHOP"), seller), 200).get("items")).hasSize(1);

      // своему боксу не пишут; по чужому запросу — нельзя
      JsonNode self = call(authed(jsonPost("/api/v1/chats", "{\"shopId\":" + shopId + "}"), seller), 400);
      assertThat(self.get("code").asText()).isEqualTo("SELF_CHAT");
   }

   // ─────────────────────── хелперы ───────────────────────

   private String activeShop(String phone, String rowCode, int slot, String name) throws Exception {
      String token = accessToken(phone, "SELLER");
      long container = jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = 'NORTH' and c.number = ?""", Long.class, rowCode, slot);
      long brand = jdbc.queryForObject("select id from brands where slug = 'chevrolet'", Long.class);
      long category = jdbc.queryForObject("select id from categories where slug = 'lights'", Long.class);
      call(authed(jsonPost("/api/v1/shops", "{\"containerId\":" + container + ",\"name\":\"" + name
            + "\",\"brandIds\":[" + brand + "],\"categoryIds\":[" + category + "]}"), token), 201);
      jdbc.update("""
            update shops set status = 'ACTIVE', verified_at = now(), open_from = '00:00', open_to = '23:59:59'
            where id = ?""", shopIdOf(phone));
      return token;
   }

   private long lacetti(String token) throws Exception {
      long model = jdbc.queryForObject("""
            select m.id from models m join brands b on b.id = m.brand_id
            where b.slug = 'chevrolet' and m.name = 'Lacetti'""", Long.class);
      return call(authed(jsonPost("/api/v1/me/cars", "{\"modelId\":" + model + ",\"year\":2008}"), token), 201)
            .get("id").asLong();
   }

   private long userId(String token) throws Exception {
      return call(authed(get("/api/v1/me"), token), 200).get("id").asLong();
   }

   private long shopIdOf(String ownerPhone) {
      return jdbc.queryForObject("select s.id from shops s join users u on u.id = s.owner_id where u.phone = ?",
            Long.class, ownerPhone);
   }
}
