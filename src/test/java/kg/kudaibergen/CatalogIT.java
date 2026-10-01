package kg.kudaibergen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Каталог: фото → черновик → публикация → поиск покупателем по машине, тексту с опечаткой, синониму
 * и номеру детали → карточка с «Подходит» и просмотром → избранное → права сотрудника → ответ «Есть»
 * с товаром и чат с карточкой. Запчасти в других тестах не создаются — счётчики точные.
 */
class CatalogIT extends AbstractIntegrationTest {

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void отЧерновикаДоПоискаИКарточки() throws Exception {
      String owner = activeShop("+996700700701", "16", 10, "Камри Центр");
      long shopId = shopIdOf("+996700700701");
      long photo = uploadPhoto(owner);

      // черновик сохраняется неполным, публикация без обязательного — 400 со списком полей
      JsonNode draft = call(authed(jsonPost("/api/v1/parts", "{\"title\":\"Стойка передняя KYB, левая\"}"), owner),
            201);
      long partId = draft.get("id").asLong();
      assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
      JsonNode incomplete = call(authed(post("/api/v1/parts/" + partId + "/publish"), owner), 400);
      assertThat(incomplete.get("code").asText()).isEqualTo("PART_INCOMPLETE");
      assertThat(incomplete.get("missing").toString()).contains("categoryId", "mediaIds", "fitments");

      JsonNode filled = call(authed(patch("/api/v1/parts/" + partId).contentType(MediaType.APPLICATION_JSON)
            .characterEncoding("UTF-8").content("""
                  {"categoryId":%d,"condition":"NEW","price":4500,"quantity":2,"manufacturer":"KYB",
                   "oemNumber":"48510-06420","mediaIds":[%d],
                   "fitments":[{"brandId":%d,"modelId":%d,"yearFrom":2006,"yearTo":2011},
                               {"brandId":%d,"modelId":%d,"yearFrom":2011,"yearTo":2017}]}"""
                  .formatted(categoryId("suspension"), photo, brandId("toyota"), camry("40"), brandId("toyota"),
                        camry("50"))), owner), 200);
      assertThat(filled.get("fitments").get(1).get("label").asText()).isEqualTo("Toyota Camry 50 · 2011–2017");
      JsonNode published = call(authed(post("/api/v1/parts/" + partId + "/publish"), owner), 200);
      assertThat(published.get("status").asText()).isEqualTo("ACTIVE");

      // покупатель с Camry 50 · 2012 находит, «Подходит»; Camry 70 — нет
      String buyer = accessToken("+996700700710", "BUYER");
      long camry50 = car(buyer, camry("50"), 2012);
      JsonNode found = call(authed(get("/api/v1/parts/search").param("carId", String.valueOf(camry50)), buyer), 200);
      assertThat(found.get("total").asInt()).isEqualTo(1);
      assertThat(found.get("appliedCar").get("label").asText()).isEqualTo("Camry 50 · 2012");
      assertThat(found.get("appliedCar").get("displayName").asText()).isEqualTo("Camry 50");
      JsonNode card = found.get("items").get(0);
      assertThat(card.get("fits").asBoolean()).isTrue();
      assertThat(card.get("exactModel").asBoolean()).isTrue();
      assertThat(card.get("mainPhoto").get("thumbUrl").asText()).contains("-320.jpg");
      assertThat(card.get("shop").get("row").asText()).isEqualTo("16");
      assertThat(card.get("stockStatus").asText()).isEqualTo("IN_STOCK");
      assertThat(card.get("currency").asText()).isEqualTo("KGS");
      assertThat(call(get("/api/v1/parts/search").param("brandId", String.valueOf(brandId("toyota")))
            .param("modelId", String.valueOf(camry("70"))).param("year", "2019"), 200).get("total").asInt()).isZero();

      // текст: опечатка, синоним, номер детали; гость без машины
      assertThat(call(get("/api/v1/parts/search").param("q", "стойка"), 200).get("total").asInt()).isEqualTo(1);
      assertThat(call(get("/api/v1/parts/search").param("q", "амортизатор"), 200).get("total").asInt()).isEqualTo(1);
      assertThat(call(get("/api/v1/parts/search").param("q", "стйока передняя"), 200).get("total").asInt())
            .isEqualTo(1);
      assertThat(call(get("/api/v1/parts/search").param("q", "48510 06420"), 200).get("total").asInt()).isEqualTo(1);
      assertThat(call(get("/api/v1/parts/search").param("q", "радиатор"), 200).get("total").asInt()).isZero();
      assertThat(call(get("/api/v1/parts/search/count").param("brandId", String.valueOf(brandId("toyota"))), 200)
            .asInt()).isEqualTo(1);
      assertThat(call(get("/api/v1/shops/" + shopId + "/parts"), 200).get("items")).hasSize(1);

      // карточка: плашка «Подходит», просмотр считается один раз
      JsonNode detail = call(authed(get("/api/v1/parts/" + partId).param("carId", String.valueOf(camry50)), buyer), 200);
      assertThat(detail.get("fit").get("fits").asBoolean()).isTrue();
      assertThat(detail.get("fit").get("carLabel").asText()).isEqualTo("Camry 50 · 2012");
      call(authed(get("/api/v1/parts/" + partId), buyer), 200);
      assertThat(jdbc.queryForObject("select views_count from parts where id = ?", Integer.class, partId))
            .isEqualTo(1);
      assertThat(call(authed(get("/api/v1/my/parts/summary"), owner), 200).get("viewsWeek").asInt()).isEqualTo(1);

      // избранное
      call(authed(put("/api/v1/parts/" + partId + "/favorite"), buyer), 204);
      assertThat(call(authed(get("/api/v1/me/favorite-parts"), buyer), 200)).hasSize(1);

      // «Нет в наличии» — пропадает из выдачи «только в наличии», но видна с inStock=false
      call(authed(patch("/api/v1/parts/" + partId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"quantity\":0}"), owner), 200);
      assertThat(call(get("/api/v1/parts/search").param("q", "стойка"), 200).get("total").asInt()).isZero();
      assertThat(call(get("/api/v1/parts/search").param("q", "стойка").param("inStock", "false"), 200)
            .get("total").asInt()).isEqualTo(1);
      JsonNode summary = call(authed(get("/api/v1/my/parts/summary"), owner), 200);
      assertThat(summary.get("outOfStock").asInt()).isEqualTo(1);

      // опубликованную нельзя «сломать»: убрать все машины — 400
      JsonNode broken = call(authed(patch("/api/v1/parts/" + partId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fitments\":[]}"), owner), 400);
      assertThat(broken.get("code").asText()).isEqualTo("PART_INCOMPLETE");

      // сотрудник правит, но не удаляет; владелец удаляет
      call(authed(jsonPost("/api/v1/my/shop/members", "{\"phone\":\"+996700700702\"}"), owner), 200);
      String staff = accessToken("+996700700702", "SELLER");
      call(authed(patch("/api/v1/parts/" + partId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"price\":4300}"), staff), 200);
      assertThat(call(authed(delete("/api/v1/parts/" + partId), staff), 403).get("code").asText())
            .isEqualTo("OWNER_ONLY");

      // у покупателя запчасть в избранном: «закончилось» и «подешевело» (пуши после коммита, асинхронно)
      long buyerId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      awaitKey("fav:OUT_OF_STOCK:" + partId + ":" + buyerId);
      awaitKey("fav:PRICE_DROP:" + partId + ":" + buyerId);
      call(authed(delete("/api/v1/parts/" + partId), owner), 204);
      call(get("/api/v1/parts/" + partId), 404);
   }

   @Test
   void ответЕстьСТоваромИЧатСКарточкой() throws Exception {
      String owner = activeShop("+996700700721", "16", 11, "Камри Юг");
      long photo = uploadPhoto(owner);
      long partId = call(authed(jsonPost("/api/v1/parts?publish=true", """
            {"title":"Фара левая Camry 50","categoryId":%d,"condition":"USED","price":7000,"mediaIds":[%d],
             "fitments":[{"brandId":%d,"modelId":%d}]}"""
            .formatted(categoryId("lights"), photo, brandId("toyota"), camry("50"))), owner), 201).get("id").asLong();

      String buyer = accessToken("+996700700730", "BUYER");
      long carId = car(buyer, camry("50"), 2013);
      long requestId = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + carId
            + ",\"text\":\"Фара левая\",\"target\":\"SHOP\",\"targetShopId\":" + shopIdOf("+996700700721") + "}"),
            buyer), 201).get("id").asLong();

      // подсказка товаров к ответу и «Есть» с товаром
      JsonNode suggested = call(authed(get("/api/v1/my/shop/requests/" + requestId + "/suggested-parts"), owner), 200);
      assertThat(suggested.findValuesAsText("id")).contains(String.valueOf(partId));
      JsonNode reply = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"HAVE\",\"condition\":\"USED\",\"partId\":" + partId + "}"), owner), 201);
      assertThat(reply.get("part").get("id").asLong()).isEqualTo(partId);
      JsonNode replies = call(authed(get("/api/v1/requests/" + requestId + "/replies"), buyer), 200);
      assertThat(replies.get(0).get("part").get("fits").asBoolean()).isTrue();

      // «Написать» с карточки: прямой чат с карточкой товара, повтор не дублирует
      long shopId = shopIdOf("+996700700721");
      JsonNode chat = call(authed(jsonPost("/api/v1/chats",
            "{\"shopId\":" + shopId + ",\"partId\":" + partId + "}"), buyer), 200);
      call(authed(jsonPost("/api/v1/chats", "{\"shopId\":" + shopId + ",\"partId\":" + partId + "}"), buyer), 200);
      JsonNode messages = call(authed(get("/api/v1/chats/" + chat.get("id").asLong() + "/messages"), buyer), 200)
            .get("items");
      assertThat(messages.findValuesAsText("type")).containsExactly("PART", "SYSTEM");
      assertThat(messages.get(0).get("payload").get("mainPhoto").get("thumbUrl").asText()).contains("-320.jpg");
   }

   @Test
   void импортИзExcelСоздаётЧерновики() throws Exception {
      String owner = activeShop("+996700700741", "16", 12, "Импорт Камри");
      byte[] template = mvc.perform(authed(get("/api/v1/my/parts/import/template"), owner)).andReturn()
            .getResponse().getContentAsByteArray();
      assertThat(template.length).isPositive();

      org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
            new java.io.ByteArrayInputStream(template));
      org.apache.poi.ss.usermodel.Row bad = workbook.getSheetAt(0).createRow(3);
      String[] values = {"Колодки", "Нет такой", "Новое", "900", "", "", "", "Toyota", "", "", ""};
      for (int i = 0; i < values.length; i++) {
         bad.createCell(i).setCellValue(values[i]);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      workbook.close();

      JsonNode report = call(authed(multipart("/api/v1/my/parts/import")
            .file(new MockMultipartFile("file", "parts.xlsx",
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray())), owner), 200);
      assertThat(report.get("created").asInt()).isEqualTo(1);
      assertThat(report.get("errors").get(0).get("row").asInt()).isEqualTo(4);
      long partId = report.get("partIds").get(0).asLong();
      JsonNode draft = call(authed(get("/api/v1/my/parts/" + partId), owner), 200);
      assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
      assertThat(draft.get("fitments")).hasSize(2);
   }

   // ─────────────────────── хелперы ───────────────────────

   /** Пуши уходят асинхронно после коммита — ждём отметку о рассылке. */
   private void awaitKey(String key) throws InterruptedException {
      for (int i = 0; i < 50 && !Boolean.TRUE.equals(redis.hasKey(key)); i++) {
         Thread.sleep(100);
      }
      assertThat(redis.hasKey(key)).as(key).isTrue();
   }

   private long uploadPhoto(String token) throws Exception {
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB), "png", png);
      JsonNode uploaded = call(authed(multipart("/api/v1/media/photos")
            .file(new MockMultipartFile("file", "part.png", "image/png", png.toByteArray()))
            .param("purpose", "PART"), token), 201);
      assertThat(uploaded.get("width").asInt()).isEqualTo(1080);
      return uploaded.get("id").asLong();
   }

   private String activeShop(String phone, String rowCode, int slot, String name) throws Exception {
      String token = accessToken(phone, "SELLER");
      long container = jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = 'NORTH' and c.number = ?""", Long.class, rowCode, slot);
      call(authed(jsonPost("/api/v1/shops", "{\"containerId\":" + container + ",\"name\":\"" + name
            + "\",\"brandIds\":[" + brandId("toyota") + "],\"categoryIds\":[" + categoryId("suspension") + "]}"),
            token), 201);
      jdbc.update("""
            update shops set status = 'ACTIVE', verified_at = now(), open_from = '00:00', open_to = '23:59:59'
            where id = ?""", shopIdOf(phone));
      return token;
   }

   private long car(String token, long modelId, int year) throws Exception {
      return call(authed(jsonPost("/api/v1/me/cars", "{\"modelId\":" + modelId + ",\"year\":" + year + "}"), token),
            201).get("id").asLong();
   }

   private long camry(String generation) {
      return jdbc.queryForObject("""
            select m.id from models m join brands b on b.id = m.brand_id
            where b.slug = 'toyota' and m.name = 'Camry' and m.generation = ?""", Long.class, generation);
   }

   private long shopIdOf(String ownerPhone) {
      return jdbc.queryForObject("select s.id from shops s join users u on u.id = s.owner_id where u.phone = ?",
            Long.class, ownerPhone);
   }

   private long brandId(String slug) {
      return jdbc.queryForObject("select id from brands where slug = ?", Long.class, slug);
   }

   private long categoryId(String slug) {
      return jdbc.queryForObject("select id from categories where slug = ?", Long.class, slug);
   }
}
