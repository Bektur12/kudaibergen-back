package kg.kudaibergen.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.chat.dto.ChatDto;
import kg.kudaibergen.chat.dto.ChatListItemDto;
import kg.kudaibergen.chat.dto.MessageDto;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.ChatChannels;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.request.PartRequestRepository;
import kg.kudaibergen.request.RequestMapper;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * Сборка DTO чата: сообщения с URL вложений и галочками, шапка (08, 13), строки списка (16) пачкой —
 * непрочитанные, последние сообщения, магазины, покупатели, запросы и «в сети» одним заходом на пачку.
 */
@Component
public class ChatView {

   private final ChatRepository chats;
   private final MessageRepository messages;
   private final ShopRepository shops;
   private final ShopMemberRepository members;
   private final ShopMapper shopMapper;
   private final UserRepository users;
   private final PartRequestRepository requests;
   private final RequestMapper requestMapper;
   private final ChatAttachments media;
   private final CentrifugoClient centrifugo;
   private final MediaService photos;

   public ChatView(ChatRepository chats, MessageRepository messages, ShopRepository shops,
                   ShopMemberRepository members, ShopMapper shopMapper, UserRepository users,
                   PartRequestRepository requests, RequestMapper requestMapper, ChatAttachments media,
                   CentrifugoClient centrifugo, MediaService photos) {
      this.chats = chats;
      this.messages = messages;
      this.shops = shops;
      this.members = members;
      this.shopMapper = shopMapper;
      this.users = users;
      this.requests = requests;
      this.requestMapper = requestMapper;
      this.media = media;
      this.centrifugo = centrifugo;
      this.photos = photos;
   }

   /** read — противоположная сторона дочитала до этого сообщения. */
   public MessageDto message(Message message, Chat chat) {
      boolean read = message.getSide() != ChatSide.SYSTEM
            && chat.readMessageId(message.getSide().other()) >= message.getId();
      return new MessageDto(message.getId(), message.getChatId(), message.getSide(), message.getSenderId(),
            message.getType(), message.getText(), message.getCode(), payload(message),
            media.urlFor(message.getMediaKey()), message.getMimeType(), message.getDurationSeconds(),
            message.getWaveform(), message.getClientId(), read, message.getCreatedAt());
   }

   /** Карточке товара — свежая ссылка на фото: presigned-ссылки в базе не хранятся, они истекают. */
   private Map<String, Object> payload(Message message) {
      if (message.getType() != MessageType.PART || message.getPayload() == null
            || !(message.getPayload().get("mediaId") instanceof Number mediaId)) {
         return message.getPayload();
      }
      Map<String, Object> payload = new LinkedHashMap<>(message.getPayload());
      payload.put("photo", photos.photos(List.of(mediaId.longValue())).get(mediaId.longValue()));
      return payload;
   }

   // ─────────────────────── шапка ───────────────────────

   public ChatDto chat(Chat chat, ChatSide mySide, Lang lang) {
      Shop shop = shops.findById(chat.getShopId()).orElseThrow();
      User buyer = users.findById(chat.getBuyerId()).orElse(null);
      List<Long> shopUsers = shopUserIds(chat.getShopId());
      boolean online;
      Instant lastSeenAt;
      if (mySide == ChatSide.BUYER) {
         online = anyOnline(shopUsers, presence(inboxes(shopUsers)));
         lastSeenAt = online ? null : users.findAllById(shopUsers).stream().map(User::getLastSeenAt)
               .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
      } else {
         online = buyer != null && anyOnline(List.of(buyer.getId()), presence(inboxes(List.of(buyer.getId()))));
         lastSeenAt = online || buyer == null ? null : buyer.getLastSeenAt();
      }
      long unread = unread(List.of(chat), mySide).getOrDefault(chat.getId(), 0L);
      ChatSide other = mySide.other();
      return new ChatDto(chat.getId(), mySide, shopMapper.card(shop),
            new ChatDto.BuyerDto(chat.getBuyerId(), buyer == null ? null : buyer.getName()),
            pinned(chat, lang), online, lastSeenAt, chat.isBlockedBy(mySide), chat.isBlockedBy(other),
            canWrite(chat, shop), unread, chat.readMessageId(other), ChatChannels.chat(chat.getId()));
   }

   private ChatDto.PinnedRequestDto pinned(Chat chat, Lang lang) {
      if (chat.getRequestId() == null) {
         return null;
      }
      return requests.findById(chat.getRequestId())
            .map(request -> new ChatDto.PinnedRequestDto(request.getId(), request.getText(),
                  requestMapper.car(request).label(), request.getStatus().name(), request.isOpen(),
                  chat.getShopId().equals(request.getClosedWithShopId())))
            .orElse(null);
   }

   /** Писать нельзя, если кто-то заблокировал собеседника или магазин не действует. */
   public static boolean canWrite(Chat chat, Shop shop) {
      return !chat.isBlocked() && shop.isActive();
   }

   // ─────────────────────── список ───────────────────────

   /** Строки списка чатов с точки зрения стороны mySide, в том же порядке. */
   public List<ChatListItemDto> rows(List<Chat> page, ChatSide mySide, Lang lang) {
      if (page.isEmpty()) {
         return List.of();
      }
      Map<Long, Long> unread = unread(page, mySide);
      Map<Long, Message> last = messages.findByIdIn(page.stream().map(Chat::getLastMessageId)
                  .filter(Objects::nonNull).toList()).stream()
            .collect(Collectors.toMap(Message::getId, Function.identity()));
      Map<Long, Shop> shopsById = shops.findAllById(page.stream().map(Chat::getShopId).distinct().toList())
            .stream().collect(Collectors.toMap(Shop::getId, Function.identity()));
      Map<Long, User> buyers = users.findAllById(page.stream().map(Chat::getBuyerId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
      Map<Long, PartRequest> requestsById = requests.findAllById(page.stream().map(Chat::getRequestId)
                  .filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(PartRequest::getId, Function.identity()));

      // «в сети»: у покупателя — кто-то из бокса, у бокса — покупатель
      Map<Long, List<Long>> counterpartUsers = new HashMap<>();
      for (Chat chat : page) {
         counterpartUsers.put(chat.getId(), mySide == ChatSide.BUYER
               ? shopUserIds(chat.getShopId()) : List.of(chat.getBuyerId()));
      }
      Map<String, Set<Long>> presence = presence(inboxes(counterpartUsers.values().stream()
            .flatMap(Collection::stream).distinct().toList()));

      List<ChatListItemDto> rows = new ArrayList<>(page.size());
      for (Chat chat : page) {
         Shop shop = shopsById.get(chat.getShopId());
         PartRequest request = chat.getRequestId() == null ? null : requestsById.get(chat.getRequestId());
         String title;
         String subtitle;
         if (mySide == ChatSide.BUYER) {
            LocationDto location = shopMapper.location(shop.getContainerId());
            String place = location.rowLabel() + " · Бокс " + location.number();
            title = shop.getName();
            subtitle = request == null ? place : request.getText() + " · " + place;
         } else {
            User buyer = buyers.get(chat.getBuyerId());
            title = buyer == null || buyer.getName() == null ? ChatTexts.buyer(lang) : buyer.getName();
            subtitle = request == null ? null : request.getText() + " · " + requestMapper.car(request).label();
         }
         Message lastMessage = last.get(chat.getLastMessageId());
         rows.add(new ChatListItemDto(chat.getId(), mySide, title, null, subtitle, chat.getRequestId(),
               request != null && !request.isOpen(), lastMessage == null ? null : lastMessage(lastMessage, mySide),
               unread.getOrDefault(chat.getId(), 0L),
               anyOnline(counterpartUsers.get(chat.getId()), presence), chat.isBlocked(),
               chat.getLastMessageAt() == null ? chat.getCreatedAt() : chat.getLastMessageAt()));
      }
      return rows;
   }

   private static ChatListItemDto.LastMessageDto lastMessage(Message message, ChatSide mySide) {
      return new ChatListItemDto.LastMessageDto(message.getId(), message.getType(), message.getText(),
            message.getCode(), message.getSide(), message.getSide() == mySide, message.getCreatedAt());
   }

   public Map<Long, Long> unread(List<Chat> page, ChatSide mySide) {
      List<Long> ids = page.stream().map(Chat::getId).toList();
      List<Object[]> counts = mySide == ChatSide.BUYER ? chats.countUnreadForBuyer(ids) : chats.countUnreadForShop(ids);
      Map<Long, Long> result = new HashMap<>();
      counts.forEach(row -> result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
      return result;
   }

   /** Бокс пользователя (владелец или сотрудник); null — не продавец. */
   public Long shopIdOf(Long userId) {
      return members.findByUserId(userId).map(ShopMember::getShopId).orElse(null);
   }

   public List<Long> shopUserIds(Long shopId) {
      return members.findByShopIdOrderByCreatedAtAsc(shopId).stream().map(ShopMember::getUserId).toList();
   }

   private Map<String, Set<Long>> presence(List<String> channels) {
      return centrifugo.presence(channels);
   }

   private static List<String> inboxes(Collection<Long> userIds) {
      return userIds.stream().map(ChatChannels::inbox).toList();
   }

   private static boolean anyOnline(Collection<Long> userIds, Map<String, Set<Long>> presence) {
      return userIds.stream().anyMatch(id -> !presence.getOrDefault(ChatChannels.inbox(id), Set.of()).isEmpty());
   }
}
