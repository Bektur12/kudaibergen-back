package kg.kudaibergen;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.request.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Запрос «Найти запчасть»: рассылка по марке → «Есть» / «Нет» → ответы и статистика покупателю → закрытие
 * с оценкой; истечение времени, продление, «Отправить всему рынку», контейнеры без фильтра по марке,
 * лимиты, окно правки ответа.
 * У каждого теста свои марки (в других классах их нет) — счётчики получателей точные при любом порядке.
 */
class RequestsIT extends AbstractIntegrationTest {

   @Autowired
   JdbcTemplate jdbc;

   @Autowired
   RequestService requestService;

   @Test
   void запросЕстьНетЗакрытиеСОценкой() throws Exception {
      String sellerA = activeShop("+996700500501", "16", 1, "subaru", "Субару Центр");
      String sellerB = activeShop("+996700500502", "18", 1, "subaru", "Forester Parts");
      String sellerMazda = activeShop("+996700500503", "16", 2, "mazda", "Мазда Бишкек");
      String pending = shop("+996700500504", "18", 2, "subaru", "Ещё на проверке");
      String closedBox = activeShop("+996700500505", "18", 3, "subaru", "Закрыто сегодня");
      call(authed(patch("/api/v1/my/shop/open").contentType(MediaType.APPLICATION_JSON)
            .content("{\"isOpen\":false}"), closedBox), 200);

      String buyer = accessToken("+996700500510", "BUYER");
      long carId = forester(buyer);

      // счётчик под выбором адресата: только проверенные открытые боксы марки, ряд сужает
      JsonNode all = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "MARKET"), buyer), 200);
      assertThat(all.get("recipients").asInt()).isEqualTo(2);
      assertThat(all.get("brand").asText()).isEqualTo("Subaru");
      JsonNode row = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "ROWS").param("rowIds", String.valueOf(rowId("16"))), buyer), 200);
      assertThat(row.get("recipients").asInt()).isEqualTo(1);

      // подсказки «что нужно»: без истории — по базовой популярности, «Стойки» несут категорию «Ходовая»
      JsonNode hints = call(authed(get("/api/v1/requests/hints").param("carId", String.valueOf(carId)), buyer), 200);
      assertThat(hints).hasSize(3);
      JsonNode struts = null;
      for (JsonNode hint : call(authed(get("/api/v1/requests/hints").param("limit", "12"), buyer), 200)) {
         if (hint.get("text").asText().equals("Стойки")) {
            struts = hint;
         }
      }

      JsonNode created = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + carId
            + ",\"text\":\"Стойки передние, пара\",\"target\":\"MARKET\",\"hintId\":" + struts.get("id").asLong()
            + "}"), buyer), 201);
      assertThat(created.get("category").get("slug").asText()).isEqualTo("suspension");
      assertThat(created.get("car").get("brand").get("shortName").asText()).isEqualTo("Subaru");
      // эта подсказка теперь первая для Forester
      assertThat(call(authed(get("/api/v1/requests/hints").param("carId", String.valueOf(carId)), buyer), 200)
            .get(0).get("text").asText()).isEqualTo("Стойки");
      long requestId = created.get("id").asLong();
      assertThat(created.get("recipientsCount").asInt()).isEqualTo(2);
      assertThat(created.get("state").asText()).isEqualTo("WAITING");
      assertThat(created.get("duration").asText()).isEqualTo("MIN_30");
      assertThat(created.get("canExtend").asBoolean()).isTrue();
      assertThat(created.get("car").get("label").asText()).isEqualTo("Subaru Forester SH · 2010");

      // лента: у получателей есть, у другой марки, непроверенного и закрытого — нет
      JsonNode feedA = call(authed(get("/api/v1/my/shop/requests"), sellerA), 200);
      assertThat(feedA.get("items")).hasSize(1);
      assertThat(feedA.get("items").get(0).get("text").asText()).isEqualTo("Стойки передние, пара");
      assertThat(feedA.get("items").get(0).get("myReply").isNull()).isTrue();
      assertThat(call(authed(get("/api/v1/my/shop/requests"), sellerMazda), 200).get("items")).isEmpty();
      assertThat(call(authed(get("/api/v1/my/shop/requests"), pending), 200).get("items")).isEmpty();
      assertThat(call(authed(get("/api/v1/my/shop/requests"), closedBox), 200).get("items")).isEmpty();
      call(authed(post("/api/v1/requests/" + requestId + "/replies").contentType(MediaType.APPLICATION_JSON)
            .content("{\"answer\":\"NOT_HAVE\"}"), sellerMazda), 404);

      // «Есть» без состояния нельзя; второй ответ того же бокса — 409
      JsonNode noCondition = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"HAVE\"}"), sellerA), 400);
      assertThat(noCondition.get("code").asText()).isEqualTo("CONDITION_REQUIRED");
      call(authed(post("/api/v1/my/shop/requests/" + requestId + "/seen"), sellerA), 204);
      JsonNode have = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"HAVE\",\"condition\":\"NEW\",\"message\":\"Есть KYB и оригинал\",\"price\":4500}"),
            sellerA), 201);
      assertThat(have.get("shop").get("name").asText()).isEqualTo("Субару Центр");
      JsonNode again = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"NOT_HAVE\"}"), sellerA), 409);
      assertThat(again.get("code").asText()).isEqualTo("ALREADY_REPLIED");
      call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies", "{\"answer\":\"NOT_HAVE\"}"), sellerB), 201);

      // покупатель видит только «Есть»; чужой покупатель запрос не видит
      JsonNode replies = call(authed(get("/api/v1/requests/" + requestId + "/replies"), buyer), 200);
      assertThat(replies).hasSize(1);
      assertThat(replies.get(0).get("price").asInt()).isEqualTo(4500);
      assertThat(replies.get(0).get("shop").get("location").get("rowCode").asText()).isEqualTo("16");
      assertThat(call(authed(get("/api/v1/requests/" + requestId + "/replies")
            .param("afterId", replies.get(0).get("id").asText()), buyer), 200)).isEmpty();
      JsonNode detail = call(authed(get("/api/v1/requests/" + requestId), buyer), 200);
      assertThat(detail.get("state").asText()).isEqualTo("HAS_ANSWERS");
      assertThat(detail.get("haveCount").asInt()).isEqualTo(1);
      assertThat(detail.get("seenCount").asInt()).isEqualTo(2);

      // статистика (32): «Есть» — с магазином и ценой, «Нет» — только место
      JsonNode stats = call(authed(get("/api/v1/requests/" + requestId + "/stats"), buyer), 200);
      assertThat(stats.get("counts").get("delivered").asInt()).isEqualTo(2);
      assertThat(stats.get("counts").get("have").asInt()).isEqualTo(1);
      assertThat(stats.get("counts").get("notHave").asInt()).isEqualTo(1);
      assertThat(stats.get("counts").get("silent").asInt()).isZero();
      assertThat(stats.get("have").get(0).get("shop").get("name").asText()).isEqualTo("Субару Центр");
      assertThat(stats.get("have").get(0).get("price").asInt()).isEqualTo(4500);
      assertThat(stats.get("notHave").get(0).get("row").asText()).isEqualTo("18");
      assertThat(stats.get("notHave").get(0).has("shop")).isFalse();
      String stranger = accessToken("+996700500511", "BUYER");
      call(authed(get("/api/v1/requests/" + requestId), stranger), 404);

      // «Нет» скрывает запрос из ленты B; у A он в «ответили «есть»»
      assertThat(call(authed(get("/api/v1/my/shop/requests"), sellerB), 200).get("items")).isEmpty();
      assertThat(call(authed(get("/api/v1/my/shop/requests").param("filter", "ANSWERED"), sellerB), 200)
            .get("items")).isEmpty();
      assertThat(call(authed(get("/api/v1/my/shop/requests").param("filter", "ANSWERED"), sellerA), 200)
            .get("items")).hasSize(1);

      // закрыть можно только с тем, кто ответил «Есть»; оценка пересчитывает рейтинг
      long shopA = shopIdOf("+996700500501");
      long shopB = shopIdOf("+996700500502");
      JsonNode notReplied = call(authed(jsonPost("/api/v1/requests/" + requestId + "/close",
            "{\"shopId\":" + shopB + ",\"stars\":4}"), buyer), 400);
      assertThat(notReplied.get("code").asText()).isEqualTo("SHOP_NOT_REPLIED");
      JsonNode closed = call(authed(jsonPost("/api/v1/requests/" + requestId + "/close",
            "{\"shopId\":" + shopA + ",\"stars\":5,\"tags\":[\"FAST_REPLY\",\"PART_OK\"]}"), buyer), 200);
      assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
      assertThat(closed.get("closedWithShopId").asLong()).isEqualTo(shopA);
      JsonNode profile = call(get("/api/v1/shops/" + shopA), 200);
      // ссылка «Поделиться» — по непредсказуемому publicId, ответ тот же
      String publicId = profile.get("publicId").asText();
      assertThat(publicId).matches("[0-9A-Za-z]{10}");
      assertThat(call(get("/api/v1/shops/public/" + publicId), 200).get("id").asLong()).isEqualTo(shopA);
      call(get("/api/v1/shops/public/nope123456"), 404);
      assertThat(profile.get("rating").asDouble()).isEqualTo(5.0);
      assertThat(profile.get("reviewsCount").asInt()).isEqualTo(1);
      String tags = jdbc.queryForObject("select array_to_string(tags, ',') from reviews where request_id = ?",
            String.class, requestId);
      assertThat(tags.split(",")).containsExactlyInAnyOrder("FAST_REPLY", "PART_OK");

      // после закрытия: ответить нельзя, у A запрос остаётся с пометкой «купили у вас»
      JsonNode late = call(authed(jsonPost("/api/v1/requests/" + requestId + "/close", "{}"), buyer), 409);
      assertThat(late.get("code").asText()).isEqualTo("REQUEST_CLOSED");
      JsonNode answered = call(authed(get("/api/v1/my/shop/requests").param("filter", "ANSWERED"), sellerA), 200);
      assertThat(answered.get("items").get(0).get("soldHere").asBoolean()).isTrue();

      JsonNode mine = call(authed(get("/api/v1/requests/my"), buyer), 200);
      assertThat(mine.get("items").get(0).get("state").asText()).isEqualTo("CLOSED");

      // статистика бокса (17): пришёл запрос, ответили «Есть», продажа; B ответил «Нет»
      JsonNode statsA = call(authed(get("/api/v1/my/shop/stats").param("period", "WEEK"), sellerA), 200);
      assertThat(statsA.get("requestsByBrands").asInt()).isEqualTo(1);
      assertThat(statsA.get("answeredHave").asInt()).isEqualTo(1);
      assertThat(statsA.get("sales").asInt()).isEqualTo(1);
      assertThat(statsA.get("unanswered").asInt()).isZero();
      assertThat(statsA.get("avgReplyMinutes").asInt()).isPositive();
      JsonNode statsB = call(authed(get("/api/v1/my/shop/stats").param("period", "MONTH"), sellerB), 200);
      assertThat(statsB.get("answeredNotHave").asInt()).isEqualTo(1);
      assertThat(statsB.get("sales").asInt()).isZero();
   }

   @Test
   void времяВышлоРасширениеИПродление() throws Exception {
      activeShop("+996700500521", "16", 4, "audi", "Субару Юг");
      String rowSeller = activeShop("+996700500522", "18", 4, "audi", "Субару Север");
      String buyer = accessToken("+996700500530", "BUYER");
      long carId = car(buyer, "audi", "A6", "C6", 2008);

      JsonNode created = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + carId
            + ",\"text\":\"Фара левая\",\"target\":\"ROWS\",\"targetRowIds\":[" + rowId("18")
            + "],\"duration\":\"HOUR_1\"}"), buyer), 201);
      long requestId = created.get("id").asLong();
      int rowRecipients = created.get("recipientsCount").asInt();
      assertThat(rowRecipients).isPositive();

      // время вышло без «Есть» → экран «Пока никто не ответил», ответить уже нельзя
      jdbc.update("update part_requests set expires_at = now() - interval '1 minute' where id = ?", requestId);
      assertThat(requestService.expireDue()).isPositive();
      JsonNode expired = call(authed(get("/api/v1/requests/" + requestId), buyer), 200);
      assertThat(expired.get("status").asText()).isEqualTo("EXPIRED");
      assertThat(expired.get("state").asText()).isEqualTo("NO_ANSWERS");
      JsonNode late = call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies",
            "{\"answer\":\"NOT_HAVE\"}"), rowSeller), 409);
      assertThat(late.get("code").asText()).isEqualTo("REQUEST_EXPIRED");
      assertThat(call(authed(get("/api/v1/my/shop/requests").param("filter", "EXPIRED"), rowSeller), 200)
            .get("items").findValuesAsText("id")).contains(String.valueOf(requestId));

      // «Отправить всему рынку» — добавляются боксы из других рядов, срок заново, запрос снова активен
      JsonNode widened = call(authed(jsonPost("/api/v1/requests/" + requestId + "/widen",
            "{\"target\":\"MARKET\"}"), buyer), 200);
      assertThat(widened.get("recipientsAdded").asInt()).isPositive();
      assertThat(widened.get("recipientsCount").asInt()).isEqualTo(rowRecipients + widened.get("recipientsAdded").asInt());
      JsonNode afterWiden = call(authed(get("/api/v1/requests/" + requestId), buyer), 200);
      assertThat(afterWiden.get("state").asText()).isEqualTo("WAITING");
      assertThat(afterWiden.get("target").asText()).isEqualTo("MARKET");
      assertThat(afterWiden.get("status").asText()).isEqualTo("ACTIVE");

      // «Нет» → в течение 10 минут можно передумать на «Есть», потом — нет
      call(authed(jsonPost("/api/v1/requests/" + requestId + "/replies", "{\"answer\":\"NOT_HAVE\"}"), rowSeller),
            201);
      JsonNode edited = call(authed(patch("/api/v1/requests/" + requestId + "/replies/mine")
            .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
            .content("{\"answer\":\"HAVE\",\"condition\":\"USED\"}"), rowSeller), 200);
      assertThat(edited.get("answer").asText()).isEqualTo("HAVE");
      assertThat(call(authed(get("/api/v1/requests/" + requestId), buyer), 200).get("haveCount").asInt())
            .isEqualTo(1);
      jdbc.update("update request_replies set created_at = now() - interval '11 minutes' where request_id = ?",
            requestId);
      JsonNode expiredEdit = call(authed(patch("/api/v1/requests/" + requestId + "/replies/mine")
            .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"NOT_HAVE\"}"), rowSeller), 409);
      assertThat(expiredEdit.get("code").asText()).isEqualTo("REPLY_EDIT_EXPIRED");

      // время вышло с ответом → «Время вышло», продлить можно 3 раза
      jdbc.update("update part_requests set expires_at = now() - interval '1 minute' where id = ?", requestId);
      requestService.expireDue();
      assertThat(call(authed(get("/api/v1/requests/" + requestId), buyer), 200).get("state").asText())
            .isEqualTo("EXPIRED");
      JsonNode badExtend = call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{\"minutes\":45}"),
            buyer), 400);
      assertThat(badExtend.get("code").asText()).isEqualTo("BAD_EXTEND");
      JsonNode extended = call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{\"minutes\":30}"),
            buyer), 200);
      assertThat(extended.get("status").asText()).isEqualTo("ACTIVE");
      assertThat(extended.get("extendedTimes").asInt()).isEqualTo(1);
      call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{\"minutes\":60}"), buyer), 200);
      JsonNode third = call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{\"minutes\":180}"),
            buyer), 200);
      assertThat(third.get("canExtend").asBoolean()).isFalse();
      JsonNode limit = call(authed(jsonPost("/api/v1/requests/" + requestId + "/extend", "{\"minutes\":30}"),
            buyer), 409);
      assertThat(limit.get("code").asText()).isEqualTo("EXTEND_LIMIT");
   }

   @Test
   void лимитОткрытыхЗапросовСвойБоксИКонтейнеры() throws Exception {
      // продавец в режиме покупателя: свой бокс запрос не получает
      String seller = activeShop("+996700500541", "16", 5, "ford", "Сам себе продавец");
      long carId = car(seller, "ford", "Focus", "II", 2008);
      JsonNode preview = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "CONTAINERS").param("containerIds", String.valueOf(containerOf("+996700500541"))),
            seller), 200);
      assertThat(preview.get("recipients").asInt()).isZero();

      // «Контейнерам» — без фильтра по марке: бокс, торгующий другой маркой, тоже получает
      activeShop("+996700500542", "16", 6, "opel", "Опель, но выбран вручную");
      JsonNode chosen = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "CONTAINERS").param("containerIds", String.valueOf(containerOf("+996700500542"))),
            seller), 200);
      assertThat(chosen.get("recipients").asInt()).isEqualTo(1);
      // число на 06б совпадает с реальной рассылкой: контейнерам — тоже без фильтра по марке
      JsonNode sentToContainer = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + carId
            + ",\"text\":\"Бампер передний\",\"target\":\"CONTAINERS\",\"targetContainerIds\":["
            + containerOf("+996700500542") + "]}"), seller), 201);
      assertThat(sentToContainer.get("recipientsCount").asInt()).isEqualTo(chosen.get("recipients").asInt());
      // всему рынку Ford продаёт только свой бокс — отправлять некому: 409, а не «пустой» запрос
      JsonNode marketEstimate = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "MARKET"), seller), 200);
      assertThat(marketEstimate.get("recipients").asInt()).isZero();
      JsonNode nobody = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + carId
            + ",\"text\":\"Бампер задний\",\"target\":\"MARKET\"}"), seller), 409);
      assertThat(nobody.get("code").asText()).isEqualTo("NO_RECIPIENTS");

      // экран 31: у контейнеров ряда — есть ли продавец и продаёт ли он марку
      JsonNode grid = call(authed(get("/api/v1/market/rows/" + rowId("16") + "/containers")
            .param("brandId", String.valueOf(brandId("opel"))), seller), 200);
      assertThat(grid.get("brandSellers").asInt()).isPositive();
      JsonNode opelSlot = null;
      JsonNode fordSlot = null;
      for (JsonNode slot : grid.get("sides").findValues("containers").stream()
            .flatMap(list -> java.util.stream.StreamSupport.stream(list.spliterator(), false)).toList()) {
         if (slot.get("id").asLong() == containerOf("+996700500542")) {
            opelSlot = slot;
         }
         if (slot.get("id").asLong() == containerOf("+996700500541")) {
            fordSlot = slot;
         }
      }
      assertThat(opelSlot.get("state").asText()).isEqualTo("HAS_SELLER");
      assertThat(opelSlot.get("sellsBrand").asBoolean()).isTrue();
      assertThat(opelSlot.get("shop").has("brandIds")).isFalse();
      assertThat(fordSlot.get("sellsBrand").asBoolean()).isFalse();

      // подсветка на карте (15): Opel — один бокс в ряду 16, ближайший — он же; карта открыта и гостю
      JsonNode highlight = call(get("/api/v1/market/map/highlight").param("kind", "BRAND")
            .param("id", String.valueOf(brandId("opel"))), 200);
      assertThat(highlight.get("name").asText()).isEqualTo("Opel");
      assertThat(highlight.get("shopsCount").asInt()).isEqualTo(1);
      assertThat(highlight.get("rowsCount").asInt()).isEqualTo(1);
      assertThat(highlight.get("rowIds").get(0).asLong()).isEqualTo(rowId("16"));
      assertThat(highlight.get("nearestRow").asText()).isEqualTo("16");
      assertThat(highlight.get("nearestContainerId").asLong()).isEqualTo(containerOf("+996700500542"));
      assertThat(highlight.get("nearestDistanceM").asInt()).isPositive();
      assertThat(highlight.get("fromSource").asText()).isEqualTo("ENTRANCE");
      JsonNode none = call(get("/api/v1/market/map/highlight").param("kind", "BRAND")
            .param("id", String.valueOf(brandId("daewoo"))), 200);
      assertThat(none.get("shopsCount").asInt()).isZero();
      assertThat(none.get("nearestRow").isNull()).isTrue();
      JsonNode noRows = call(authed(get("/api/v1/requests/estimate").param("carId", String.valueOf(carId))
            .param("target", "ROWS"), seller), 400);
      assertThat(noRows.get("code").asText()).isEqualTo("ROWS_REQUIRED");

      String buyer = accessToken("+996700500550", "BUYER");
      long buyerCar = car(buyer, "ford", "Focus", "II", 2008);
      for (int i = 0; i < 10; i++) {
         call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + buyerCar + ",\"text\":\"Деталь " + i
               + "\",\"target\":\"MARKET\"}"), buyer), 201);
      }
      JsonNode limit = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + buyerCar
            + ",\"text\":\"Одиннадцатая\",\"target\":\"MARKET\"}"), buyer), 409);
      assertThat(limit.get("code").asText()).isEqualTo("OPEN_REQUESTS_LIMIT");

      // «Мои запросы» постранично, без повторов
      JsonNode first = call(authed(get("/api/v1/requests/my").param("limit", "6"), buyer), 200);
      JsonNode second = call(authed(get("/api/v1/requests/my").param("limit", "6")
            .param("cursor", first.get("nextCursor").asText()), buyer), 200);
      assertThat(first.get("items")).hasSize(6);
      assertThat(second.get("items")).hasSize(4);
      assertThat(second.get("nextCursor").isNull()).isTrue();

      JsonNode tooShort = call(authed(jsonPost("/api/v1/requests", "{\"carId\":" + buyerCar
            + ",\"text\":\"ab\",\"target\":\"MARKET\"}"), buyer), 400);
      assertThat(tooShort.toString()).contains("text");
   }

   // ─────────────────────── хелперы ───────────────────────

   /** Магазин на проверке: владелец входит, регистрирует бокс на свободном месте ряда. */
   private String shop(String phone, String rowCode, int slot, String brandSlug, String name) throws Exception {
      String token = accessToken(phone, "SELLER");
      long container = jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = ? and c.side = 'NORTH' and c.number = ?""", Long.class, rowCode, slot);
      long category = jdbc.queryForObject("select id from categories where slug = 'suspension'", Long.class);
      call(authed(jsonPost("/api/v1/shops", "{\"containerId\":" + container + ",\"name\":\"" + name
            + "\",\"brandIds\":[" + brandId(brandSlug) + "],\"categoryIds\":[" + category + "]}"), token), 201);
      return token;
   }

   /** Проверенный магазин, открытый круглые сутки — чтобы тест не зависел от времени запуска. */
   private String activeShop(String phone, String rowCode, int slot, String brandSlug, String name) throws Exception {
      String token = shop(phone, rowCode, slot, brandSlug, name);
      jdbc.update("""
            update shops set status = 'ACTIVE', verified_at = now(), open_from = '00:00', open_to = '23:59:59'
            where id = ?""", shopIdOf(phone));
      return token;
   }

   private long forester(String token) throws Exception {
      return car(token, "subaru", "Forester", "SH", 2010);
   }

   private long car(String token, String brandSlug, String model, String generation, int year) throws Exception {
      long modelId = jdbc.queryForObject("""
            select m.id from models m join brands b on b.id = m.brand_id
            where b.slug = ? and m.name = ? and m.generation = ?""", Long.class, brandSlug, model, generation);
      return call(authed(jsonPost("/api/v1/me/cars", "{\"modelId\":" + modelId + ",\"year\":" + year + "}"),
            token), 201).get("id").asLong();
   }

   private long shopIdOf(String ownerPhone) {
      return jdbc.queryForObject("select s.id from shops s join users u on u.id = s.owner_id where u.phone = ?",
            Long.class, ownerPhone);
   }

   private long containerOf(String ownerPhone) {
      return jdbc.queryForObject("select s.container_id from shops s join users u on u.id = s.owner_id"
            + " where u.phone = ?", Long.class, ownerPhone);
   }

   private long rowId(String code) {
      return jdbc.queryForObject("select id from market_rows where code = ?", Long.class, code);
   }

   private long brandId(String slug) {
      return jdbc.queryForObject("select id from brands where slug = ?", Long.class, slug);
   }
}
