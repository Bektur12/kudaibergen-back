package kg.kudaibergen.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.kudaibergen.chat.dto.ChatResponse;
import kg.kudaibergen.chat.dto.MessageResponse;
import kg.kudaibergen.chat.dto.PresenceEvent;
import kg.kudaibergen.chat.dto.ReadEvent;
import kg.kudaibergen.chat.dto.SendMessageRequest;
import kg.kudaibergen.chat.dto.TypingEvent;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.notification.OutboxService;
import kg.kudaibergen.store.DealAccess;
import kg.kudaibergen.store.StoreRepository;
import kg.kudaibergen.store.entity.Store;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.User;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Service
public class ChatService implements DealAccess {

   private static final Pattern CHAT_MESSAGES_TOPIC = Pattern.compile("^/topic/chats/(\\d+)$");

   private final ChatRepository chats;
   private final MessageRepository messages;
   private final StoreRepository stores;
   private final UserService userService;
   private final OutboxService outbox;
   private final ChatMediaStorage mediaStorage;
   private final SimpMessagingTemplate ws;

   /** Активные WS-сессии на пользователя — несколько вкладок/устройств это нормально,
    * офлайн только когда сессий не осталось совсем. In-memory: один инстанс бэкенда. */
   private final Map<Long, Set<String>> onlineSessions = new ConcurrentHashMap<>();

   /** sessionId -> (subscriptionId -> chatId), только подписки на /topic/chats/{id} —
    * признак "юзер прямо сейчас смотрит на этот чат", нужен для условной отправки пуша. */
   private final Map<String, Map<String, Long>> chatViewSubscriptions = new ConcurrentHashMap<>();

   public ChatService(ChatRepository chats, MessageRepository messages, StoreRepository stores,
                      UserService userService, OutboxService outbox, ChatMediaStorage mediaStorage,
                      SimpMessagingTemplate ws) {
      this.chats = chats;
      this.messages = messages;
      this.stores = stores;
      this.userService = userService;
      this.outbox = outbox;
      this.mediaStorage = mediaStorage;
      this.ws = ws;
   }

   /** Чат либо уже есть, либо создаётся — создание чата и есть заключение сделки. */
   @Transactional
   public Chat getOrCreate(Long buyerId, Long storeId, Long requestId) {
      return chats.findByBuyerIdAndStoreId(buyerId, storeId)
            .orElseGet(() -> {
               Chat created = chats.save(new Chat(buyerId, storeId, requestId));
               stores.findById(storeId).ifPresent(Store::incrementDeals);
               broadcastInboxUpdate(created);
               return created;
            });
   }

   /** Признак сделки для показа телефонов и отзывов (п.8 ТЗ) — есть чат между покупателем и магазином. */
   @Override
   @Transactional(readOnly = true)
   public boolean hasAcceptedDeal(Long buyerId, Long storeId) {
      return buyerId != null && storeId != null && chats.existsByBuyerIdAndStoreId(buyerId, storeId);
   }

   @Transactional(readOnly = true)
   public List<ChatResponse> list(Long userId) {
      Long storeId = stores.findByOwnerId(userId).map(Store::getId).orElse(null);
      List<Chat> found = chats.findForParticipant(userId, storeId);
      if (found.isEmpty()) {
         return List.of();
      }
      Set<Long> unread = Set.copyOf(messages.findChatIdsWithUnread(
            found.stream().map(Chat::getId).toList(), userId));
      return found.stream()
            .map(chat -> toResponse(chat, unread.contains(chat.getId()), userId))
            .toList();
   }

   /** Покупатель пишет магазину напрямую с карточки магазина, без запроса/предложения. */
   @Transactional
   public ChatResponse startChat(Long buyerId, Long storeId) {
      Long ownerId = ownerOf(storeId);
      if (ownerId == null) {
         throw new NotFoundException("STORE_NOT_FOUND", "Магазин не найден");
      }
      if (ownerId.equals(buyerId)) {
         throw new BadRequestException("SELF_CHAT", "Нельзя написать самому себе");
      }
      Chat chat = getOrCreate(buyerId, storeId, null);
      boolean hasUnread = messages.countUnread(chat.getId(), buyerId) > 0;
      return toResponse(chat, hasUnread, buyerId);
   }

   private ChatResponse toResponse(Chat chat, boolean hasUnread, Long viewerId) {
      Long otherUserId = viewerId.equals(chat.getBuyerId()) ? ownerOf(chat.getStoreId()) : chat.getBuyerId();
      boolean online = isOnline(otherUserId);
      Instant lastSeenAt = online || otherUserId == null ? null : userService.lastSeenAt(otherUserId);
      return new ChatResponse(chat.getId(), chat.getRequestId(), chat.getBuyerId(), chat.getStoreId(),
            stores.findName(chat.getStoreId()), chat.getLastMessage(), chat.getLastMessageAt(), hasUnread,
            chat.getCreatedAt(), online, lastSeenAt);
   }

   @Transactional(readOnly = true)
   public PageResponse<MessageResponse> messages(Long chatId, Long userId, int page, int size) {
      requireParticipant(chatId, userId);
      return PageResponse.of(messages.findByChatIdOrderByCreatedAtDesc(chatId, PageRequest.of(page, size)),
            message -> MessageResponse.of(message, mediaStorage::urlFor));
   }

   @Transactional
   public MessageResponse send(Long chatId, Long userId, SendMessageRequest request) {
      Chat chat = requireParticipant(chatId, userId);
      Message message = messages.save(new Message(chatId, userId, request.body().trim(), "TEXT"));
      chat.touch(message.getBody(), message.getCreatedAt());
      notifyRecipient(chat, userId, message.getBody());
      MessageResponse response = MessageResponse.of(message, mediaStorage::urlFor);
      broadcast(chatId, response);
      broadcastInboxUpdate(chat);
      return response;
   }

   @Transactional
   public MessageResponse sendMedia(Long chatId, Long userId, MultipartFile file, String type, String caption,
                                    Integer durationSeconds) {
      Chat chat = requireParticipant(chatId, userId);
      ChatMediaStorage.Stored stored = mediaStorage.store(file, type);
      Message message = messages.save(new Message(chatId, userId, caption == null ? "" : caption.trim(), type,
            stored.key(), stored.mimeType(), durationSeconds));
      chat.touch(previewOf(type), message.getCreatedAt());
      notifyRecipient(chat, userId, previewOf(type));
      MessageResponse response = MessageResponse.of(message, mediaStorage::urlFor);
      broadcast(chatId, response);
      broadcastInboxUpdate(chat);
      return response;
   }

   /** Пуш уже сохранённого сообщения всем, кто сейчас подписан на этот чат по WebSocket. */
   private void broadcast(Long chatId, MessageResponse message) {
      ws.convertAndSend("/topic/chats/" + chatId, message);
   }

   private String previewOf(String type) {
      return switch (type) {
         case "PHOTO" -> "📷 Фото";
         case "VOICE" -> "🎤 Голосовое сообщение";
         case "VIDEO" -> "🎥 Видео";
         default -> "Новое сообщение";
      };
   }

   /** Пуш нужен только тому, кто не увидит сообщение живьём — то есть не подписан прямо
    * сейчас на /topic/chats/{id}. Просто "онлайн" недостаточно: человек может быть в
    * сети, но сидеть на другом экране (список чатов, лента и т.д.). */
   private void notifyRecipient(Chat chat, Long senderId, String previewText) {
      Long recipientId = senderId.equals(chat.getBuyerId()) ? ownerOf(chat.getStoreId()) : chat.getBuyerId();
      if (isViewingChat(recipientId, chat.getId())) {
         return;
      }
      User sender = userService.getRequired(senderId);
      outbox.enqueueNewMessage(recipientId, chat.getId(),
            sender.getName() == null ? "Новое сообщение" : sender.getName(), previewText);
   }

   @Transactional
   public void markRead(Long chatId, Long userId) {
      Chat chat = requireParticipant(chatId, userId);
      Instant now = Instant.now();
      messages.markRead(chatId, userId, now);
      ws.convertAndSend("/topic/chats/" + chatId + "/read", new ReadEvent(userId, now));
      broadcastInboxUpdate(chat);
   }

   /** Для WebSocket-подписки: бросает, если userId не участник чата. */
   @Transactional(readOnly = true)
   public void assertParticipant(Long chatId, Long userId) {
      requireParticipant(chatId, userId);
   }

   /** Участник чата — покупатель или владелец магазина. Остальным доступа нет. */
   private Chat requireParticipant(Long chatId, Long userId) {
      Chat chat = chats.findById(chatId)
            .orElseThrow(() -> new NotFoundException("CHAT_NOT_FOUND", "Чат не найден"));
      boolean buyer = chat.getBuyerId().equals(userId);
      boolean seller = userId.equals(ownerOf(chat.getStoreId()));
      if (!buyer && !seller) {
         throw new ForbiddenException("CHAT_FORBIDDEN", "Чат принадлежит другим участникам");
      }
      return chat;
   }

   private Long ownerOf(Long storeId) {
      return stores.findOwnerUserIds(List.of(storeId)).stream().findFirst().orElse(null);
   }

   /**
    * Живой инбокс: пушит актуальную строку чата обеим сторонам в их персональный топик
    * /topic/users/{id}/chats — тот же формат, что и GET /chats, без нового DTO. Нужно, чтобы
    * список чатов обновлялся сам, без pull-to-refresh, даже если конкретный чат не открыт.
    */
   private void broadcastInboxUpdate(Chat chat) {
      Long ownerId = ownerOf(chat.getStoreId());
      pushInboxRow(chat, chat.getBuyerId());
      if (ownerId != null) {
         pushInboxRow(chat, ownerId);
      }
   }

   private void pushInboxRow(Chat chat, Long viewerId) {
      boolean hasUnread = messages.countUnread(chat.getId(), viewerId) > 0;
      ws.convertAndSend("/topic/users/" + viewerId + "/chats", toResponse(chat, hasUnread, viewerId));
   }

   // ─────────────────────── typing / presence (WebSocket-only) ───────────────────────

   /** Ретранслирует «печатает» от одного участника чата другому. Без истории, без БД. */
   public void broadcastTyping(Long chatId, Long userId, boolean typing) {
      ws.convertAndSend("/topic/chats/" + chatId + "/typing", new TypingEvent(userId, typing));
   }

   @EventListener
   public void onSessionConnected(SessionConnectedEvent event) {
      AuthPrincipal principal = principalOf(event.getMessage());
      if (principal == null) {
         return;
      }
      Set<String> sessions = onlineSessions.computeIfAbsent(principal.userId(), id -> ConcurrentHashMap.newKeySet());
      boolean wasOffline = sessions.isEmpty();
      sessions.add(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
      if (wasOffline) {
         broadcastPresence(principal.userId(), true);
      }
   }

   @EventListener
   public void onSessionDisconnected(SessionDisconnectEvent event) {
      AuthPrincipal principal = principalOf(event.getMessage());
      if (principal == null) {
         return;
      }
      Set<String> sessions = onlineSessions.get(principal.userId());
      if (sessions == null) {
         return;
      }
      sessions.remove(event.getSessionId());
      chatViewSubscriptions.remove(event.getSessionId());
      if (sessions.isEmpty()) {
         onlineSessions.remove(principal.userId());
         userService.touchLastSeen(principal.userId(), Instant.now());
         broadcastPresence(principal.userId(), false);
      }
   }

   @EventListener
   public void onSessionSubscribed(SessionSubscribeEvent event) {
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
      Matcher matcher = CHAT_MESSAGES_TOPIC.matcher(String.valueOf(accessor.getDestination()));
      if (!matcher.matches()) {
         return;
      }
      chatViewSubscriptions.computeIfAbsent(accessor.getSessionId(), id -> new ConcurrentHashMap<>())
            .put(accessor.getSubscriptionId(), Long.valueOf(matcher.group(1)));
   }

   @EventListener
   public void onSessionUnsubscribed(SessionUnsubscribeEvent event) {
      StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
      Map<String, Long> subs = chatViewSubscriptions.get(accessor.getSessionId());
      if (subs != null) {
         subs.remove(accessor.getSubscriptionId());
      }
   }

   /** Есть ли у userId сейчас живая подписка на /topic/chats/{chatId} (хоть с одного устройства). */
   private boolean isViewingChat(Long userId, Long chatId) {
      Set<String> sessions = onlineSessions.get(userId);
      if (sessions == null) {
         return false;
      }
      return sessions.stream().anyMatch(sessionId -> {
         Map<String, Long> subs = chatViewSubscriptions.get(sessionId);
         return subs != null && subs.containsValue(chatId);
      });
   }

   /** Кому это интересно — все чаты пользователя, ровно тот же набор, что отдаёт GET /chats. */
   private void broadcastPresence(Long userId, boolean online) {
      Long storeId = stores.findByOwnerId(userId).map(Store::getId).orElse(null);
      PresenceEvent event = new PresenceEvent(userId, online);
      chats.findForParticipant(userId, storeId)
            .forEach(chat -> ws.convertAndSend("/topic/chats/" + chat.getId() + "/presence", event));
   }

   private boolean isOnline(Long userId) {
      if (userId == null) {
         return false;
      }
      Set<String> sessions = onlineSessions.get(userId);
      return sessions != null && !sessions.isEmpty();
   }

   /**
    * SessionConnectedEvent/SessionDisconnectEvent оборачивают исходный CONNECT/DISCONNECT
    * фрейм — сессионные атрибуты (в т.ч. наш AuthPrincipal) лежат не в заголовках самого
    * события, а внутри вложенного оригинального сообщения.
    */
   private AuthPrincipal principalOf(org.springframework.messaging.Message<byte[]> message) {
      Map<String, Object> sessionAttributes = StompHeaderAccessor.wrap(message).getSessionAttributes();
      if (sessionAttributes == null) {
         Object nested = message.getHeaders().get(SimpMessageHeaderAccessor.CONNECT_MESSAGE_HEADER);
         if (nested == null) {
            nested = message.getHeaders().get(SimpMessageHeaderAccessor.DISCONNECT_MESSAGE_HEADER);
         }
         if (nested instanceof org.springframework.messaging.Message<?> original) {
            sessionAttributes = StompHeaderAccessor.wrap(original).getSessionAttributes();
         }
      }
      Object attr = sessionAttributes == null ? null : sessionAttributes.get(ChatWebSocketInterceptor.PRINCIPAL_ATTR);
      return attr instanceof AuthPrincipal principal ? principal : null;
   }
}
