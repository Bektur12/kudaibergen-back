package kg.kudaibergen;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Сквозной сценарий продукта: запрос веером → массовый ответ шаблоном (сам создаёт чат и
 * прилетает покупателю сообщением, без отдельного экрана предложений) → отзыв → аналитика.
 * Город «Ош» выбран, чтобы сценарий не пересекался с магазинами из других тестов.
 */
class FanOutRequestIT extends AbstractIntegrationTest {

   private static final String CITY = "Ош";

   @Test
   void полныйПутьОтЗапросаДоАналитики() throws Exception {
      // ── продавец с нужной категорией и филиалом в городе покупателя
      String seller = register("+996700200201", "SELLER", "Азиз");
      call(authed(jsonPatch("/api/v1/my-store",
            "{\"name\":\"АвтоПрофи\",\"description\":\"Тормозная система и масла\"}"), seller), 200);
      call(authed(jsonPut("/api/v1/my-store/categories", "{\"categories\":[\"BRAKES\",\"OILS\"]}"), seller), 200);
      call(authed(jsonPost("/api/v1/my-store/branches",
            "{\"address\":\"ул. Ленина 1\",\"city\":\"" + CITY + "\",\"phone\":\"+996555111222\"}"), seller), 201);

      // ── продавец из той же локации, но с другой категорией — ему запрос уйти не должен
      String otherSeller = register("+996700200202", "SELLER", "ПодвескаПлюс");
      call(authed(jsonPut("/api/v1/my-store/categories", "{\"categories\":[\"SUSPENSION\"]}"), otherSeller), 200);
      call(authed(jsonPost("/api/v1/my-store/branches",
            "{\"address\":\"ул. Курманжан Датка 5\",\"city\":\"" + CITY + "\"}"), otherSeller), 201);

      // витрина покупателя фильтрует по категории и городу
      JsonNode shopWindow = call(authed(get("/api/v1/stores?category=BRAKES&city=" + CITY), seller), 200);
      assertThat(shopWindow.get("totalElements").asLong()).isEqualTo(1);
      assertThat(shopWindow.get("content").get(0).get("name").asText()).isEqualTo("АвтоПрофи");

      // ── покупатель с машиной в профиле
      String buyer = register("+996700200203", "BUYER", "Азамат");
      call(authed(jsonPatch("/api/v1/me", "{\"city\":\"" + CITY + "\"}"), buyer), 200);
      JsonNode vehicle = call(authed(jsonPost("/api/v1/me/vehicles",
            "{\"brand\":\"Toyota\",\"model\":\"Camry\",\"year\":2018,\"engine\":\"2.5 бензин\",\"isDefault\":true}"),
            buyer), 201);
      assertThat(vehicle.get("title").asText()).isEqualTo("Toyota Camry 2018, 2.5 бензин");

      // ── веерная рассылка: совпал только один магазин из двух
      JsonNode created = call(authed(jsonPost("/api/v1/requests", """
            {"category":"BRAKES","description":"Передние колодки, оригинал или хорошая копия",
             "vehicleId":%d,"budgetMin":2000,"budgetMax":4000,"isUrgent":false}
            """.formatted(vehicle.get("id").asLong()))
            .header("Idempotency-Key", UUID.randomUUID().toString()), buyer), 201);
      long requestId = created.get("id").asLong();
      assertThat(created.get("sellersMatched").asInt()).isEqualTo(1);

      // ── лента продавца: запрос виден, машина подставлена, ответа ещё нет
      JsonNode feed = call(authed(get("/api/v1/my-store/requests?filter=UNANSWERED"), seller), 200);
      assertThat(feed.get("totalElements").asLong()).isEqualTo(1);
      JsonNode row = feed.get("content").get(0);
      assertThat(row.get("requestId").asLong()).isEqualTo(requestId);
      assertThat(row.get("car").asText()).isEqualTo("Toyota Camry 2018, 2.5 бензин");
      assertThat(row.get("repliedAt").isNull()).isTrue();

      JsonNode otherFeed = call(authed(get("/api/v1/my-store/requests"), otherSeller), 200);
      assertThat(otherFeed.get("totalElements").asLong()).isZero();

      // чужой магазин не может ответить на запрос, который ему не приходил
      JsonNode forbidden = call(authed(jsonPost("/api/v1/offers",
            "{\"requestId\":" + requestId + ",\"price\":3000}"), otherSeller), 403);
      assertThat(forbidden.get("code").asText()).isEqualTo("REQUEST_NOT_FOR_STORE");

      // ── до ответа продавца чата и, соответственно, телефона филиала ещё нет
      long storeIdBeforeOffer = call(authed(get("/api/v1/stores?category=BRAKES&city=" + CITY), buyer), 200)
            .get("content").get(0).get("id").asLong();
      JsonNode storeBefore = call(authed(get("/api/v1/stores/" + storeIdBeforeOffer), buyer), 200);
      assertThat(storeBefore.get("branches").get(0).get("phone").isNull()).isTrue();

      // ── массовый ответ шаблоном: покупателю не нужно заходить в отдельный список
      // предложений — оффер сразу создаёт чат и прилетает туда сообщением
      JsonNode template = call(authed(jsonPost("/api/v1/my-store/templates",
            "{\"title\":\"Есть в наличии\",\"body\":\"Есть в наличии, приходите сегодня\"}"), seller), 201);
      JsonNode bulk = call(authed(jsonPost("/api/v1/offers/bulk",
            "{\"requestIds\":[" + requestId + "],\"templateId\":" + template.get("id").asLong()
                  + ",\"price\":3200}")
            .header("Idempotency-Key", UUID.randomUUID().toString()), seller), 200);
      assertThat(bulk.get("created").asInt()).isEqualTo(1);
      assertThat(bulk.get("skipped").asInt()).isZero();

      // ── чат с офером уже есть у покупателя — без единого явного действия «написать»
      JsonNode buyerChats = call(authed(get("/api/v1/chats"), buyer), 200);
      assertThat(buyerChats.get(0).get("hasUnread").asBoolean()).isTrue();
      assertThat(buyerChats.get(0).get("lastMessage").asText())
            .contains("3200 сом")
            .contains("Есть в наличии, приходите сегодня");

      // повтор на тот же запрос — дубля предложения не будет
      JsonNode secondBulk = call(authed(jsonPost("/api/v1/offers/bulk",
            "{\"requestIds\":[" + requestId + "],\"templateId\":" + template.get("id").asLong() + "}")
            .header("Idempotency-Key", UUID.randomUUID().toString()), seller), 200);
      assertThat(secondBulk.get("created").asInt()).isZero();
      assertThat(secondBulk.get("skipped").asInt()).isEqualTo(1);

      assertThat(call(authed(get("/api/v1/my-store/requests?filter=UNANSWERED"), seller), 200)
            .get("totalElements").asLong()).isZero();

      // ── структурные данные предложения по-прежнему доступны (список/сравнение по цене)
      JsonNode details = call(authed(get("/api/v1/requests/" + requestId), buyer), 200);
      assertThat(details.get("offerCount").asInt()).isEqualTo(1);
      JsonNode offer = details.get("offers").get(0);
      assertThat(offer.get("storeName").asText()).isEqualTo("АвтоПрофи");
      assertThat(offer.get("comment").asText()).isEqualTo("Есть в наличии, приходите сегодня");
      long storeId = offer.get("storeId").asLong();

      // телефон магазина уже доступен — чат (а с ним и сделка) открылся ещё на этапе оффера
      JsonNode storeAfter = call(authed(get("/api/v1/stores/" + storeId), buyer), 200);
      assertThat(storeAfter.get("branches").get(0).get("phone").asText()).isEqualTo("+996555111222");

      // POST /stores/{id}/chat идемпотентен — просто достаём id уже существующего чата
      long chatId = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200)
            .get("id").asLong();

      // ── чат
      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Здравствуйте, когда можно забрать?\"}"), buyer), 201);
      JsonNode sellerChats = call(authed(get("/api/v1/chats"), seller), 200);
      assertThat(sellerChats.get(0).get("hasUnread").asBoolean()).isTrue();
      assertThat(sellerChats.get(0).get("lastMessage").asText()).isEqualTo("Здравствуйте, когда можно забрать?");

      // ── отзыв после сделки поднимает рейтинг магазина
      call(authed(jsonPost("/api/v1/stores/" + storeId + "/reviews",
            "{\"rating\":5,\"text\":\"Быстро ответили\"}"), buyer), 201);
      assertThat(call(authed(get("/api/v1/stores/" + storeId), buyer), 200)
            .get("rating").asDouble()).isEqualTo(5.0);

      // ── аналитика считается из request_recipients + offers
      JsonNode analytics = call(authed(get("/api/v1/my-store/analytics?period=WEEK"), seller), 200);
      assertThat(analytics.get("requestsReceived").asLong()).isEqualTo(1);
      assertThat(analytics.get("requestsAnswered").asLong()).isEqualTo(1);
      assertThat(analytics.get("responseRate").asDouble()).isEqualTo(1.0);
      assertThat(analytics.get("dealsClosed").asLong()).isEqualTo(1);
      assertThat(analytics.get("topDemandedCategories").get(0).get("category").asText()).isEqualTo("BRAKES");
   }

   private MockHttpServletRequestBuilder jsonPut(String url, String body) {
      return put(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
   }

   private MockHttpServletRequestBuilder jsonPatch(String url, String body) {
      return patch(url).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
   }
}
