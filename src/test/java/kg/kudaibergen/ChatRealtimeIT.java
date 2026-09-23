package kg.kudaibergen;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.chat.dto.TypingRequest;
import kg.kudaibergen.notification.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Проверяет живой путь сообщения end-to-end: HTTP POST -> сохранение -> STOMP push
 * подписчику, без ручного обновления. Реальный сокет на реальном порту (RANDOM_PORT),
 * не MockMvc — иначе брокер и хендшейк не запускаются по-настоящему.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatRealtimeIT extends AbstractIntegrationTest {

   @LocalServerPort
   private int port;

   @Autowired
   private NotificationOutboxRepository outboxRepository;

   @Test
   void текстовоеСообщениеДолетаетПоВебсокету() throws Exception {
      String seller = register("+996700500501", "SELLER", "Нурлан");
      String buyer = register("+996700500502", "BUYER", "Диана");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      StompSession sellerSession = connect(seller);
      CompletableFuture<JsonNode> received = new CompletableFuture<>();
      sellerSession.subscribe("/topic/chats/" + chatId, handlerFor(received));

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Здравствуйте!\"}"), buyer), 201);

      JsonNode pushed = received.get(10, TimeUnit.SECONDS);
      assertThat(pushed.get("body").asText()).isEqualTo("Здравствуйте!");
      assertThat(pushed.get("chatId").asLong()).isEqualTo(chatId);

      sellerSession.disconnect();
   }

   @Test
   void фотоТожеДолетаетПоВебсокету() throws Exception {
      String seller = register("+996700500503", "SELLER", "Азат");
      String buyer = register("+996700500504", "BUYER", "Салтанат");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      StompSession sellerSession = connect(seller);
      CompletableFuture<JsonNode> received = new CompletableFuture<>();
      sellerSession.subscribe("/topic/chats/" + chatId, handlerFor(received));

      var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3, 4 });
      mvc.perform(multipart("/api/v1/chats/" + chatId + "/messages/media")
                  .file(file)
                  .param("type", "PHOTO")
                  .header("Authorization", "Bearer " + buyer))
            .andReturn();

      JsonNode pushed = received.get(10, TimeUnit.SECONDS);
      assertThat(pushed.get("type").asText()).isEqualTo("PHOTO");
      assertThat(pushed.get("mediaUrl").asText()).isNotBlank();

      sellerSession.disconnect();
   }

   @Test
   void печатаетДолетаетПоВебсокету() throws Exception {
      String seller = register("+996700500505", "SELLER", "Данияр");
      String buyer = register("+996700500506", "BUYER", "Айгерим");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      StompSession sellerSession = connect(seller);
      StompSession buyerSession = connect(buyer);
      CompletableFuture<JsonNode> received = new CompletableFuture<>();
      sellerSession.subscribe("/topic/chats/" + chatId + "/typing", handlerFor(received));

      buyerSession.send("/app/chats/" + chatId + "/typing", new TypingRequest(true));

      JsonNode pushed = received.get(10, TimeUnit.SECONDS);
      assertThat(pushed.get("userId").asLong()).isEqualTo(buyerUserId);
      assertThat(pushed.get("typing").asBoolean()).isTrue();

      sellerSession.disconnect();
      buyerSession.disconnect();
   }

   @Test
   void статусВСетиДолетаетПоВебсокетуИЧерезРест() throws Exception {
      String seller = register("+996700500507", "SELLER", "Эрлан");
      String buyer = register("+996700500508", "BUYER", "Жамиля");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();
      assertThat(chat.get("otherOnline").asBoolean()).isFalse();

      StompSession buyerSession = connect(buyer);
      BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
      buyerSession.subscribe("/topic/chats/" + chatId + "/presence", queueHandlerFor(events));

      StompSession sellerSession = connect(seller);
      JsonNode onlineEvent = events.poll(10, TimeUnit.SECONDS);
      assertThat(onlineEvent).isNotNull();
      assertThat(onlineEvent.get("userId").asLong()).isEqualTo(sellerUserId);
      assertThat(onlineEvent.get("online").asBoolean()).isTrue();

      JsonNode chatWhileOnline = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      assertThat(chatWhileOnline.get("otherOnline").asBoolean()).isTrue();

      sellerSession.disconnect();
      JsonNode offlineEvent = events.poll(10, TimeUnit.SECONDS);
      assertThat(offlineEvent).isNotNull();
      assertThat(offlineEvent.get("online").asBoolean()).isFalse();

      JsonNode chatAfter = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      assertThat(chatAfter.get("otherOnline").asBoolean()).isFalse();
      assertThat(chatAfter.get("otherLastSeenAt").isNull()).isFalse();

      buyerSession.disconnect();
   }

   @Test
   void новоеСообщениеОбновляетИнбоксБезОткрытогоЧата() throws Exception {
      String seller = register("+996700500509", "SELLER", "Максат");
      String buyer = register("+996700500510", "BUYER", "Нурай");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long sellerUserId = call(authed(get("/api/v1/me"), seller), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      // продавец сидит на экране списка чатов — конкретный /topic/chats/{id} не открывал
      StompSession sellerSession = connect(seller);
      CompletableFuture<JsonNode> inboxUpdate = new CompletableFuture<>();
      sellerSession.subscribe("/topic/users/" + sellerUserId + "/chats", handlerFor(inboxUpdate));

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Когда доставка?\"}"), buyer), 201);

      JsonNode row = inboxUpdate.get(10, TimeUnit.SECONDS);
      assertThat(row.get("id").asLong()).isEqualTo(chatId);
      assertThat(row.get("lastMessage").asText()).isEqualTo("Когда доставка?");
      assertThat(row.get("hasUnread").asBoolean()).isTrue();

      sellerSession.disconnect();
   }

   @Test
   void прочтениеДолетаетОтправителюБезПерезапроса() throws Exception {
      String seller = register("+996700500511", "SELLER", "Бекзат");
      String buyer = register("+996700500512", "BUYER", "Айнура");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии\"}"), seller), 201);

      // продавец-отправитель подписан на read, чтобы увидеть галочку без перезапроса истории
      StompSession sellerSession = connect(seller);
      CompletableFuture<JsonNode> readEvent = new CompletableFuture<>();
      sellerSession.subscribe("/topic/chats/" + chatId + "/read", handlerFor(readEvent));

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/read", "{}"), buyer), 204);

      JsonNode pushed = readEvent.get(10, TimeUnit.SECONDS);
      assertThat(pushed.get("userId").asLong()).isEqualTo(buyerUserId);
      assertThat(pushed.get("readAt").asText()).isNotBlank();

      sellerSession.disconnect();
   }

   @Test
   void пушНеШлётсяЕслиПолучательСмотритЭтотЧат() throws Exception {
      String seller = register("+996700500513", "SELLER", "Дамир");
      String buyer = register("+996700500514", "BUYER", "Асель");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      // покупатель "открыл экран чата" — подписан ровно на топик сообщений
      StompSession buyerSession = connect(buyer);
      CompletableFuture<JsonNode> received = new CompletableFuture<>();
      buyerSession.subscribe("/topic/chats/" + chatId, handlerFor(received));

      long before = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии?\"}"), seller), 201);
      received.get(10, TimeUnit.SECONDS); // дождаться живой доставки — тогда пуш точно уже решён

      long after = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();
      assertThat(after).isEqualTo(before);

      buyerSession.disconnect();
   }

   @Test
   void пушШлётсяЕслиПолучательНеСмотритЭтотЧат() throws Exception {
      String seller = register("+996700500515", "SELLER", "Нурбек");
      String buyer = register("+996700500516", "BUYER", "Гүлнара");

      long storeId = call(authed(get("/api/v1/my-store"), seller), 200).get("id").asLong();
      long buyerUserId = call(authed(get("/api/v1/me"), buyer), 200).get("id").asLong();
      JsonNode chat = call(authed(jsonPost("/api/v1/stores/" + storeId + "/chat", "{}"), buyer), 200);
      long chatId = chat.get("id").asLong();

      // покупатель не подключён к сокету вообще — сообщение он живьём не увидит
      long before = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId)).count();

      call(authed(jsonPost("/api/v1/chats/" + chatId + "/messages",
            "{\"body\":\"Есть в наличии?\"}"), seller), 201);

      var found = outboxRepository.findAll().stream()
            .filter(n -> n.getUserId().equals(buyerUserId))
            .toList();
      assertThat(found).hasSize((int) before + 1);
      assertThat(found.get(found.size() - 1).getPayload()).contains("NEW_MESSAGE").contains(String.valueOf(chatId));
   }

   private StompSession connect(String accessToken) throws Exception {
      WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
      stompClient.setMessageConverter(new MappingJackson2MessageConverter());

      StompHeaders connectHeaders = new StompHeaders();
      connectHeaders.add("Authorization", "Bearer " + accessToken);

      CompletableFuture<StompSession> future = new CompletableFuture<>();
      stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
            new StompSessionHandlerAdapter() {
               @Override
               public void afterConnected(StompSession session, StompHeaders headers) {
                  future.complete(session);
               }

               @Override
               public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                           byte[] payload, Throwable exception) {
                  future.completeExceptionally(exception);
               }

               @Override
               public void handleTransportError(StompSession session, Throwable exception) {
                  future.completeExceptionally(exception);
               }
            });
      return future.get(10, TimeUnit.SECONDS);
   }

   private StompFrameHandler handlerFor(CompletableFuture<JsonNode> sink) {
      return new StompFrameHandler() {
         @Override
         public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
         }

         @Override
         public void handleFrame(StompHeaders headers, Object payload) {
            try {
               sink.complete(json.readTree((byte[]) payload));
            } catch (Exception ex) {
               sink.completeExceptionally(ex);
            }
         }
      };
   }

   private StompFrameHandler queueHandlerFor(BlockingQueue<JsonNode> sink) {
      return new StompFrameHandler() {
         @Override
         public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
         }

         @Override
         public void handleFrame(StompHeaders headers, Object payload) {
            try {
               sink.add(json.readTree((byte[]) payload));
            } catch (Exception ex) {
               throw new IllegalStateException(ex);
            }
         }
      };
   }
}
