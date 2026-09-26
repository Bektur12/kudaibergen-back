package kg.kudaibergen;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.notification.NotificationOutboxRepository;
import kg.kudaibergen.support.FakeCentrifugo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Проверяет контракт бэкенда с Centrifugo (см. CentrifugoClient, ChatService): правильный
 * канал, правильный конверт (ChatEvent), правильное решение push/skip по presence, правильный
 * online-статус. Настоящей доставки по WebSocket здесь нет — это уже ответственность самого
 * Centrifugo, отдельного проверенного продукта, а не то, что мы реализуем сами. FakeCentrifugo
 * поднимает вместо него локальный HTTP-сервер, реализующий ровно тот кусок Server API, которым
 * пользуется CentrifugoClient (publish/batch presence).
 */
class ChatRealtimeIT extends AbstractIntegrationTest {

   private static final FakeCentrifugo CENTRIFUGO = new FakeCentrifugo();

   @Autowired
   private NotificationOutboxRepository outboxRepository;

   @Autowired
   private AppProperties appProperties;

   @BeforeAll
   static void startCentrifugo() {
      CENTRIFUGO.start();
   }

   @AfterAll
   static void stopCentrifugo() {
      CENTRIFUGO.stop();
   }

   @BeforeEach
   void resetCentrifugo() {
      CENTRIFUGO.reset();
   }

   @DynamicPropertySource
   static void centrifugo(DynamicPropertyRegistry registry) {
      registry.add("app.centrifugo.api-url", CENTRIFUGO::apiUrl);
   }

   @Test
   void текстовоеСообщениеПубликуетсяВКаналЧата() throws Exception {
      String seller = register("+996700500501", "SELLER", "Нурлан");
      String buyer = register("+996700500502", "BUYER", "Диана");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();
      String expectedChannel = chatChannel(chatId, buyerUserId, sellerUserId);
      assertThat(chat.get("channel").asText()).isEqualTo(expectedChannel);

      drainPending(); // публикации живого инбокса от создания чата нас тут не интересуют

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Здравствуйте!\"}"), buyer), 201);

      FakeCentrifugo.Published event = awaitOnChannel(expectedChannel);
      assertThat(event.data().get("type").asText()).isEqualTo("MESSAGE");
      assertThat(event.data().get("payload").get("body").asText()).isEqualTo("Здравствуйте!");
      assertThat(event.data().get("payload").get("chatId").asLong()).isEqualTo(chatId);
   }

   @Test
   void фотоТожеПубликуетсяВКаналЧата() throws Exception {
      String seller = register("+996700500503", "SELLER", "Азат");
      String buyer = register("+996700500504", "BUYER", "Салтанат");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();
      String expectedChannel = chatChannel(chatId, buyerUserId, sellerUserId);

      drainPending();

      var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3, 4 });
      mvc.perform(multipart("/api/v1/chats/" + chatId + "/messages/media")
                  .file(file)
                  .param("type", "PHOTO")
                  .header("Authorization", "Bearer " + buyer))
            .andReturn();

      FakeCentrifugo.Published event = awaitOnChannel(expectedChannel);
      assertThat(event.data().get("type").asText()).isEqualTo("MESSAGE");
      assertThat(event.data().get("payload").get("type").asText()).isEqualTo("PHOTO");
      assertThat(event.data().get("payload").get("mediaUrl").asText()).isNotBlank();
   }

   @Test
   void прочтениеПубликуетReadВКаналЧата() throws Exception {
      String seller = register("+996700500511", "SELLER", "Бекзат");
      String buyer = register("+996700500512", "BUYER", "Айнура");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();
      String expectedChannel = chatChannel(chatId, buyerUserId, sellerUserId);

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии\"}"), seller), 201);
      drainPending();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/read", "{}"), buyer), 204);

      FakeCentrifugo.Published event = awaitOnChannel(expectedChannel);
      assertThat(event.data().get("type").asText()).isEqualTo("READ");
      assertThat(event.data().get("payload").get("userId").asLong()).isEqualTo(buyerUserId);
      assertThat(event.data().get("payload").get("readAt").asText()).isNotBlank();
   }

   @Test
   void пушНеШлётсяЕслиПолучательПрисутствуетНаКаналеЧата() throws Exception {
      String seller = register("+996700500513", "SELLER", "Дамир");
      String buyer = register("+996700500514", "BUYER", "Асель");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      // покупатель "смотрит" этот чат прямо сейчас — presence на канале чата это отражает
      CENTRIFUGO.present(chatChannel(chatId, buyerUserId, sellerUserId), buyerUserId);

      long before = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии?\"}"), seller), 201);
      awaitOnChannel(chatChannel(chatId, buyerUserId, sellerUserId)); // дождаться, пока send() решит вопрос push/skip

      long after = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();
      assertThat(after).isEqualTo(before);
   }

   @Test
   void пушШлётсяЕслиПолучательНеПрисутствуетНаКаналеЧата() throws Exception {
      String seller = register("+996700500515", "SELLER", "Нурбек");
      String buyer = register("+996700500516", "BUYER", "Гүлнара");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      // presence для этого канала не настроен — покупатель "не смотрит" чат
      long before = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии?\"}"), seller), 201);
      awaitOnChannel(chatChannel(chatId, buyerUserId, sellerUserId));

      var found = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId))
            .toList();
      assertThat(found).hasSize((int) before + 1);
      assertThat(found.get(found.size() - 1).getPayload()).contains("NEW_MESSAGE").contains(String.valueOf(chatId));
   }

   @Test
   void otherOnlineОпределяетсяПоPresenceНаЛичномКаналеИнбокса() throws Exception {
      String seller = register("+996700500507", "SELLER", "Эрлан");
      String buyer = register("+996700500508", "BUYER", "Жамиля");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();

      JsonNode offline = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      assertThat(offline.get("otherOnline").asBoolean()).isFalse();
      assertThat(offline.get("otherLastSeenAt").isNull()).isFalse();

      CENTRIFUGO.present("inbox:" + sellerUserId + "#" + sellerUserId, sellerUserId);

      JsonNode online = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      assertThat(online.get("otherOnline").asBoolean()).isTrue();
      assertThat(online.get("otherLastSeenAt").isNull()).isTrue();
   }

   @Test
   void realtimeТокенПодписанСекретомCentrifugoИСодержитUserId() throws Exception {
      String buyer = register("+996700500520", "BUYER", "Марат");
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();

      JsonNode response = call(authed(get("/api/v1/realtime/token"), buyer), 200);
      assertThat(response.get("expiresInSeconds").asLong()).isGreaterThan(0);

      SecretKey key = Keys.hmacShaKeyFor(
            appProperties.centrifugo().tokenSecret().getBytes(StandardCharsets.UTF_8));
      Claims claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(response.get("token").asText())
            .getPayload();
      assertThat(claims.getSubject()).isEqualTo(String.valueOf(buyerUserId));
      assertThat(claims.getExpiration()).isAfter(Date.from(Instant.now()));
   }

   private static String chatChannel(long chatId, long buyerId, long sellerId) {
      long a = Math.min(buyerId, sellerId);
      long b = Math.max(buyerId, sellerId);
      return "chat:" + chatId + "#" + a + "," + b;
   }

   /** Публикации бэкенда в Centrifugo идут асинхронно — ждём, пока конкретный канал не получит
    * событие, не полагаясь на порядок между параллельными async-задачами (сообщение + 2 строки
    * живого инбокса публикуются независимо и могут прийти в любом порядке). */
   private FakeCentrifugo.Published awaitOnChannel(String channel) throws InterruptedException {
      List<String> seen = new ArrayList<>();
      Duration deadline = Duration.ofSeconds(10);
      long start = System.nanoTime();
      while (Duration.ofNanos(System.nanoTime() - start).compareTo(deadline) < 0) {
         FakeCentrifugo.Published event = CENTRIFUGO.takePublish(Duration.ofSeconds(2));
         if (event == null) {
            continue;
         }
         if (event.channel().equals(channel)) {
            return event;
         }
         seen.add(event.channel());
      }
      throw new AssertionError("Не дождались публикации в канал " + channel + ", видели: " + seen);
   }

   /** Вычищает публикации от предыдущих шагов сценария (например, живой инбокс от создания чата),
    * чтобы они не попадались следующему awaitOnChannel в этом же тесте. */
   private void drainPending() throws InterruptedException {
      while (CENTRIFUGO.takePublish(Duration.ofMillis(300)) != null) {
         // отбрасываем — это события предыдущего шага, не то, что проверяет текущий шаг
      }
   }
}
