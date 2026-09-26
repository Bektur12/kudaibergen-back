package kg.kudaibergen.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.dto.ChatResponse;
import kg.kudaibergen.chat.dto.MessageResponse;
import kg.kudaibergen.chat.dto.ReadEvent;
import kg.kudaibergen.chat.dto.SendMessageRequest;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.notification.OutboxService;
import kg.kudaibergen.store.DealAccess;
import kg.kudaibergen.store.StoreRepository;
import kg.kudaibergen.store.entity.Store;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Живой чат: REST — для истории, отправки и бизнес-логики, Centrifugo — для live-доставки уже
 * сохранённых событий (см. CentrifugoClient) и presence. Сокет тут только push, ничего не решает
 * сам — если Centrifugo недоступен, чат продолжает работать по REST + пуш-уведомлениям.
 */
@Service
public class ChatService implements DealAccess {

   private final ChatRepository chats;
   private final MessageRepository messages;
   private final StoreRepository stores;
   private final UserService userService;
   private final OutboxService outbox;
   private final ChatMediaStorage mediaStorage;
   private final CentrifugoClient centrifugo;
   private final TransactionTemplate tx;
   private final ObjectMapper objectMapper;

   public ChatService(ChatRepository chats, MessageRepository messages, StoreRepository stores,
                      UserService userService, OutboxService outbox, ChatMediaStorage mediaStorage,
                      CentrifugoClient centrifugo, TransactionTemplate tx, ObjectMapper objectMapper) {
      this.chats = chats;
      this.messages = messages;
      this.stores = stores;
      this.userService = userService;
      this.outbox = outbox;
      this.mediaStorage = mediaStorage;
      this.centrifugo = centrifugo;
      this.tx = tx;
      this.objectMapper = objectMapper;
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

      Map<Long, Long> ownerByStore = new HashMap<>();
      List<String> inboxChannels = new ArrayList<>();
      for (Chat chat : found) {
         Long ownerId = ownerByStore.computeIfAbsent(chat.getStoreId(), this::ownerOf);
         Long otherUserId = userId.equals(chat.getBuyerId()) ? ownerId : chat.getBuyerId();
         if (otherUserId != null) {
            inboxChannels.add(ChatChannels.inbox(otherUserId));
         }
      }
      Map<String, Set<Long>> presence = centrifugo.presentUserIdsBatch(inboxChannels);

      return found.stream()
            .map(chat -> {
               Long ownerId = ownerByStore.get(chat.getStoreId());
               Long otherUserId = userId.equals(chat.getBuyerId()) ? ownerId : chat.getBuyerId();
               boolean online = otherUserId != null
                     && !presence.getOrDefault(ChatChannels.inbox(otherUserId), Set.of()).isEmpty();
               return toResponse(chat, unread.contains(chat.getId()), userId, ownerId, online);
            })
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

   /** Одиночный вызов (не список) — считает online отдельным presence-запросом. */
   private ChatResponse toResponse(Chat chat, boolean hasUnread, Long viewerId) {
      Long ownerId = ownerOf(chat.getStoreId());
      Long otherUserId = viewerId.equals(chat.getBuyerId()) ? ownerId : chat.getBuyerId();
      boolean online = otherUserId != null && !centrifugo.presentUserIds(ChatChannels.inbox(otherUserId)).isEmpty();
      return toResponse(chat, hasUnread, viewerId, ownerId, online);
   }

   private ChatResponse toResponse(Chat chat, boolean hasUnread, Long viewerId, Long ownerId, boolean online) {
      Long otherUserId = viewerId.equals(chat.getBuyerId()) ? ownerId : chat.getBuyerId();
      Instant lastSeenAt = online || otherUserId == null ? null : userService.lastSeenAt(otherUserId);
      String channel = ChatChannels.chat(chat.getId(), chat.getBuyerId(), ownerId);
      return new ChatResponse(chat.getId(), chat.getRequestId(), chat.getBuyerId(), chat.getStoreId(),
            stores.findName(chat.getStoreId()), chat.getLastMessage(), chat.getLastMessageAt(), hasUnread,
            chat.getCreatedAt(), online, lastSeenAt, channel);
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
      broadcast(chat, response);
      broadcastInboxUpdate(chat);
      return response;
   }

   /** Загрузка в хранилище идёт вне транзакции: пока файл летит в S3, соединение с БД не занято. */
   public MessageResponse sendMedia(Long chatId, Long userId, MultipartFile file, String type, String caption,
                                    Integer durationSeconds, String waveformJson) {
      requireParticipant(chatId, userId);
      String waveform = "VOICE".equals(type) ? normalizeWaveform(waveformJson) : null;
      ChatMediaStorage.Stored stored = mediaStorage.store(file, type);
      return tx.execute(status -> {
         Chat chat = requireParticipant(chatId, userId);
         Message message = messages.save(new Message(chatId, userId, caption == null ? "" : caption.trim(), type,
               stored.key(), stored.mimeType(), durationSeconds));
         message.setWaveform(waveform);
         chat.touch(previewOf(type), message.getCreatedAt());
         notifyRecipient(chat, userId, previewOf(type));
         MessageResponse response = MessageResponse.of(message, mediaStorage::urlFor);
         broadcast(chat, response);
         broadcastInboxUpdate(chat);
         return response;
      });
   }

   private static final int MAX_WAVEFORM_POINTS = 100;

   /** Проверяет JSON-массив пиков (<= 100 чисел в диапазоне 0..1) и возвращает его в нормализованном виде. */
   private String normalizeWaveform(String json) {
      if (json == null || json.isBlank()) {
         return null;
      }
      List<Double> peaks;
      try {
         peaks = objectMapper.readValue(json, new TypeReference<List<Double>>() {
         });
      } catch (Exception e) {
         throw new BadRequestException("BAD_WAVEFORM", "waveform должен быть JSON-массивом чисел", "waveform");
      }
      if (peaks.isEmpty() || peaks.size() > MAX_WAVEFORM_POINTS) {
         throw new BadRequestException("BAD_WAVEFORM",
               "waveform: от 1 до %d значений".formatted(MAX_WAVEFORM_POINTS), "waveform");
      }
      for (Double peak : peaks) {
         if (peak == null || peak.isNaN() || peak < 0 || peak > 1) {
            throw new BadRequestException("BAD_WAVEFORM", "Значения waveform должны быть в диапазоне 0..1",
                  "waveform");
         }
      }
      try {
         return objectMapper.writeValueAsString(peaks);
      } catch (Exception e) {
         throw new BadRequestException("BAD_WAVEFORM", "Некорректный waveform", "waveform");
      }
   }

   /** Пуш уже сохранённого сообщения обоим участникам чата, кто сейчас подписан на канал. */
   private void broadcast(Chat chat, MessageResponse message) {
      String channel = ChatChannels.chat(chat.getId(), chat.getBuyerId(), ownerOf(chat.getStoreId()));
      centrifugo.publish(channel, ChatEvent.message(message));
   }

   private String previewOf(String type) {
      return switch (type) {
         case "PHOTO" -> "📷 Фото";
         case "VOICE" -> "🎤 Голосовое сообщение";
         case "VIDEO" -> "🎥 Видео";
         default -> "Новое сообщение";
      };
   }

   /** Пуш нужен только тому, кто не увидит сообщение живьём — то есть не подписан прямо сейчас
    * на канал этого чата в Centrifugo (presence на chat:{id}#..., см. ChatChannels). Просто
    * "онлайн" недостаточно: человек может быть в сети, но сидеть на другом экране. */
   private void notifyRecipient(Chat chat, Long senderId, String previewText) {
      Long ownerId = ownerOf(chat.getStoreId());
      Long recipientId = senderId.equals(chat.getBuyerId()) ? ownerId : chat.getBuyerId();
      String channel = ChatChannels.chat(chat.getId(), chat.getBuyerId(), ownerId);
      if (centrifugo.presentUserIds(channel).contains(recipientId)) {
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
      String channel = ChatChannels.chat(chat.getId(), chat.getBuyerId(), ownerOf(chat.getStoreId()));
      centrifugo.publish(channel, ChatEvent.read(new ReadEvent(userId, now)));
      broadcastInboxUpdate(chat);
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
    * Живой инбокс: пушит актуальную строку чата обеим сторонам в их личный канал inbox:{id}#{id}
    * (тот же формат, что и GET /chats, без нового DTO). Нужно, чтобы список чатов обновлялся сам,
    * без pull-to-refresh, даже если конкретный чат не открыт.
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
      centrifugo.publish(ChatChannels.inbox(viewerId), toResponse(chat, hasUnread, viewerId));
   }
}
