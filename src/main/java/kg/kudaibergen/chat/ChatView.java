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
import kg.kudaibergen.master.MasterMapper;
import kg.kudaibergen.master.MasterRepository;
import kg.kudaibergen.master.ServiceRequestMapper;
import kg.kudaibergen.master.ServiceRequestRepository;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceRequest;
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

   /** code сообщения, скрытого модерацией. */
   public static final String HIDDEN_CODE = "HIDDEN_BY_ADMIN";

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
   private final ChatProviders providers;
   private final MasterRepository masters;
   private final MasterMapper masterMapper;
   private final ServiceRequestRepository serviceRequests;
   private final ServiceRequestMapper serviceMapper;

   public ChatView(ChatRepository chats, MessageRepository messages, ShopRepository shops,
                   ShopMemberRepository members, ShopMapper shopMapper, UserRepository users,
                   PartRequestRepository requests, RequestMapper requestMapper, ChatAttachments media,
                   CentrifugoClient centrifugo, MediaService photos, ChatProviders providers,
                   MasterRepository masters, MasterMapper masterMapper, ServiceRequestRepository serviceRequests,
                   ServiceRequestMapper serviceMapper) {
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
      this.providers = providers;
      this.masters = masters;
      this.masterMapper = masterMapper;
      this.serviceRequests = serviceRequests;
      this.serviceMapper = serviceMapper;
   }

   /** read — противоположная сторона дочитала до этого сообщения. */
   public MessageDto message(Message message, Chat chat) {
      boolean read = message.getSide() != ChatSide.SYSTEM
            && chat.readMessageId(message.getSide().other()) >= message.getId();
      if (message.isHiddenByAdmin()) {
         // скрыто модерацией: без текста и вложений, клиент показывает «Сообщение скрыто администрацией»
         return new MessageDto(message.getId(), message.getChatId(), message.getSide(), message.getSenderId(),
               message.getType(), null, HIDDEN_CODE, null, null, null, null, null, message.getClientId(), read,
               message.getCreatedAt());
      }
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
      payload.put("mainPhoto", photos.photos(List.of(mediaId.longValue())).get(mediaId.longValue()));
      return payload;
   }

   // ─────────────────────── шапка ───────────────────────

   public ChatDto chat(Chat chat, ChatSide mySide, Lang lang) {
      Shop shop = providers.shop(chat).orElse(null);
      Master master = providers.master(chat).orElse(null);
      User buyer = users.findById(chat.getBuyerId()).orElse(null);
      List<Long> providerUsers = providers.userIds(chat);
      boolean online;
      Instant lastSeenAt;
      if (mySide == ChatSide.BUYER) {
         online = anyOnline(providerUsers, presence(inboxes(providerUsers)));
         lastSeenAt = online ? null : users.findAllById(providerUsers).stream().map(User::getLastSeenAt)
               .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
      } else {
         online = buyer != null && anyOnline(List.of(buyer.getId()), presence(inboxes(List.of(buyer.getId()))));
         lastSeenAt = online || buyer == null ? null : buyer.getLastSeenAt();
      }
      long unread = unread(List.of(chat), mySide).getOrDefault(chat.getId(), 0L);
      ChatSide other = mySide.other();
      return new ChatDto(chat.getId(), mySide, shop == null ? null : shopMapper.card(shop),
            master == null ? null : masterMapper.card(master),
            new ChatDto.BuyerDto(chat.getBuyerId(), buyer == null ? null : buyer.getName(),
                  buyer == null ? null : photos.thumbUrl(buyer.getAvatarMediaId())),
            pinned(chat, lang), pinnedService(chat), online, lastSeenAt, chat.isBlockedBy(mySide),
            chat.isBlockedBy(other), canWrite(chat), unread, chat.readMessageId(other), ChatChannels.chat(chat.getId()));
   }

   private ChatDto.PinnedRequestDto pinned(Chat chat, Lang lang) {
      if (chat.getRequestId() == null) {
         return null;
      }
      return requests.findById(chat.getRequestId())
            .map(request -> new ChatDto.PinnedRequestDto(request.getId(), request.getText(),
                  requestMapper.car(request).label(), request.getStatus().name(), request.isOpen(),
                  Objects.equals(chat.getShopId(), request.getClosedWithShopId())))
            .orElse(null);
   }

   /** Заявка на услугу в чате с мастером: «Не заводится · Toyota Camry 50 · 2012». soldHere — договорились. */
   private ChatDto.PinnedRequestDto pinnedService(Chat chat) {
      if (chat.getServiceRequestId() == null) {
         return null;
      }
      return serviceRequests.findById(chat.getServiceRequestId())
            .map(request -> new ChatDto.PinnedRequestDto(request.getId(), request.getDescription(),
                  serviceMapper.car(request).label(), request.getStatus().name(), request.isOpen(),
                  Objects.equals(chat.getMasterId(), request.getClosedWithMasterId())))
            .orElse(null);
   }

   /** Писать нельзя, если кто-то заблокировал собеседника или исполнитель не действует. */
   public boolean canWrite(Chat chat) {
      return !chat.isBlocked() && providers.active(chat);
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
      Map<Long, Shop> shopsById = shops.findAllById(page.stream().map(Chat::getShopId).filter(Objects::nonNull)
                  .distinct().toList())
            .stream().collect(Collectors.toMap(Shop::getId, Function.identity()));
      Map<Long, Master> mastersById = masters.findAllById(page.stream().map(Chat::getMasterId).filter(Objects::nonNull)
                  .distinct().toList())
            .stream().collect(Collectors.toMap(Master::getId, Function.identity()));
      Map<Long, User> buyers = users.findAllById(page.stream().map(Chat::getBuyerId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
      Map<Long, PartRequest> requestsById = requests.findAllById(page.stream().map(Chat::getRequestId)
                  .filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(PartRequest::getId, Function.identity()));
      Map<Long, ServiceRequest> serviceById = serviceRequests.findAllById(page.stream().map(Chat::getServiceRequestId)
                  .filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(ServiceRequest::getId, Function.identity()));

      // «в сети»: у покупателя — кто-то из исполнителя, у исполнителя — покупатель
      Map<Long, List<Long>> counterpartUsers = new HashMap<>();
      for (Chat chat : page) {
         counterpartUsers.put(chat.getId(), mySide == ChatSide.BUYER ? providerUsers(chat, mastersById)
               : List.of(chat.getBuyerId()));
      }
      Map<String, Set<Long>> presence = presence(inboxes(counterpartUsers.values().stream()
            .flatMap(Collection::stream).distinct().toList()));

      List<Long> avatarIds = new ArrayList<>();
      shopsById.values().forEach(shop -> avatarIds.add(shop.getAvatarMediaId()));
      mastersById.values().forEach(master -> avatarIds.add(master.getAvatarMediaId()));
      buyers.values().forEach(buyer -> avatarIds.add(buyer.getAvatarMediaId()));
      Map<Long, String> avatars = photos.thumbUrls(avatarIds);

      List<ChatListItemDto> rows = new ArrayList<>(page.size());
      for (Chat chat : page) {
         PartRequest request = chat.getRequestId() == null ? null : requestsById.get(chat.getRequestId());
         ServiceRequest service = chat.getServiceRequestId() == null ? null : serviceById.get(chat.getServiceRequestId());
         String topic = request != null ? request.getText() : service != null ? service.getDescription() : null;
         String title;
         String subtitle;
         String avatarUrl;
         if (mySide == ChatSide.BUYER && chat.withMaster()) {
            Master master = mastersById.get(chat.getMasterId());
            title = master == null ? "" : master.getName();
            subtitle = topic == null ? (master == null ? null : master.getAddress()) : shorten(topic);
            avatarUrl = master == null ? null : avatars.get(master.getAvatarMediaId());
         } else if (mySide == ChatSide.BUYER) {
            Shop shop = shopsById.get(chat.getShopId());
            LocationDto location = shopMapper.location(shop.getContainerId());
            String place = location.rowLabel() + " · Бокс " + location.number();
            title = shop.getName();
            subtitle = topic == null ? place : topic + " · " + place;
            avatarUrl = avatars.get(shop.getAvatarMediaId());
         } else {
            User buyer = buyers.get(chat.getBuyerId());
            title = buyer == null || buyer.getName() == null ? ChatTexts.buyer(lang) : buyer.getName();
            String car = request != null ? requestMapper.car(request).label()
                  : service != null ? serviceMapper.car(service).label() : null;
            subtitle = topic == null ? null : shorten(topic) + " · " + car;
            avatarUrl = buyer == null ? null : avatars.get(buyer.getAvatarMediaId());
         }
         boolean closed = request != null ? !request.isOpen() : service != null && !service.isOpen();
         Message lastMessage = last.get(chat.getLastMessageId());
         rows.add(new ChatListItemDto(chat.getId(), mySide, title, avatarUrl, subtitle, chat.getRequestId(),
               chat.getServiceRequestId(), chat.getMasterId(), closed,
               lastMessage == null ? null : lastMessage(lastMessage, mySide), unread.getOrDefault(chat.getId(), 0L),
               anyOnline(counterpartUsers.get(chat.getId()), presence), chat.isBlocked(),
               chat.getLastMessageAt() == null ? chat.getCreatedAt() : chat.getLastMessageAt()));
      }
      return rows;
   }

   private List<Long> providerUsers(Chat chat, Map<Long, Master> mastersById) {
      if (chat.withMaster()) {
         Master master = mastersById.get(chat.getMasterId());
         return master == null ? List.of() : List.of(master.getOwnerId());
      }
      return shopUserIds(chat.getShopId());
   }

   private static String shorten(String text) {
      return text.length() <= 60 ? text : text.substring(0, 59) + "…";
   }

   private static ChatListItemDto.LastMessageDto lastMessage(Message message, ChatSide mySide) {
      boolean hidden = message.isHiddenByAdmin();
      return new ChatListItemDto.LastMessageDto(message.getId(), message.getType(), hidden ? null : message.getText(),
            hidden ? HIDDEN_CODE : message.getCode(), message.getSide(), message.getSide() == mySide, message.getCreatedAt());
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
