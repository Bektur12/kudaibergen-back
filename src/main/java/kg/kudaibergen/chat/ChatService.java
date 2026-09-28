package kg.kudaibergen.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.chat.dto.ChatDto;
import kg.kudaibergen.chat.dto.ChatInputs;
import kg.kudaibergen.chat.dto.ChatListItemDto;
import kg.kudaibergen.chat.dto.MessageDto;
import kg.kudaibergen.chat.dto.QuickReplyDto;
import kg.kudaibergen.chat.dto.UnreadDto;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.chat.entity.QuickReply;
import kg.kudaibergen.chat.entity.SystemEvent;
import kg.kudaibergen.chat.realtime.CentrifugoTokens;
import kg.kudaibergen.chat.realtime.ChatChannels;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.complaint.ComplaintDto;
import kg.kudaibergen.complaint.ComplaintService;
import kg.kudaibergen.complaint.ComplaintType;
import kg.kudaibergen.request.PartRequestRepository;
import kg.kudaibergen.request.RequestEvents;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Чат покупателя с магазином (ТЗ 11): REST — история, отправка и все правила; Centrifugo — только
 * живая доставка уже сохранённого (ChatRealtime). Без Centrifugo чат работает на REST и пушах.
 */
@Service
public class ChatService {

   private static final int MAX_WAVEFORM_POINTS = 100;
   /** Первая страница списка: всё, что раньше «бесконечности». */
   private static final Instant LIST_START = Instant.parse("9999-12-31T00:00:00Z");

   private final ChatRepository chats;
   private final MessageRepository messages;
   private final ReplyTemplateRepository templates;
   private final ChatAccess access;
   private final ChatView view;
   private final ChatAttachments media;
   private final CentrifugoTokens tokens;
   private final ShopRepository shops;
   private final ShopAccess shopAccess;
   private final ShopMapper shopMapper;
   private final PartRequestRepository requests;
   private final PartRepository parts;
   private final UserRepository users;
   private final ComplaintService complaints;
   private final ApplicationEventPublisher events;
   private final TransactionTemplate tx;
   private final ObjectMapper json;
   private final AppProperties.Media mediaConfig;
   private final Clock clock;

   public ChatService(ChatRepository chats, MessageRepository messages, ReplyTemplateRepository templates,
                      ChatAccess access, ChatView view, ChatAttachments media, CentrifugoTokens tokens,
                      ShopRepository shops, ShopAccess shopAccess, ShopMapper shopMapper,
                      PartRequestRepository requests, PartRepository parts, UserRepository users, ComplaintService complaints,
                      ApplicationEventPublisher events, TransactionTemplate tx, ObjectMapper json,
                      AppProperties properties, Clock clock) {
      this.chats = chats;
      this.messages = messages;
      this.templates = templates;
      this.access = access;
      this.view = view;
      this.media = media;
      this.tokens = tokens;
      this.shops = shops;
      this.shopAccess = shopAccess;
      this.shopMapper = shopMapper;
      this.requests = requests;
      this.parts = parts;
      this.users = users;
      this.complaints = complaints;
      this.events = events;
      this.tx = tx;
      this.json = json;
      this.mediaConfig = properties.media();
      this.clock = clock;
   }

   // ─────────────────────── открыть чат ───────────────────────

   /**
    * «Написать» покупателя (07, 29, 30). По запросу — чат, созданный ответом «Есть»; без запроса —
    * прямой чат с магазином (создаётся при первом открытии, бокс увидит его после первого сообщения).
    */
   @Transactional
   public ChatDto open(Long buyerId, ChatInputs.OpenChat input, Lang lang) {
      Shop shop = shops.findById(input.shopId()).filter(Shop::isActive)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      if (shop.getId().equals(view.shopIdOf(buyerId))) {
         throw new BadRequestException("SELF_CHAT", "Нельзя написать своему боксу");
      }
      Chat chat;
      if (input.requestId() != null) {
         PartRequest request = requests.findById(input.requestId())
               .filter(found -> found.getBuyerId().equals(buyerId))
               .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден"));
         chat = chats.findByBuyerIdAndShopIdAndRequestId(buyerId, shop.getId(), request.getId())
               .orElseThrow(() -> new BadRequestException("SHOP_NOT_REPLIED",
                     "Этот бокс не отвечал «Есть» на запрос"));
      } else {
         chat = chats.findDirect(buyerId, shop.getId()).orElseGet(() -> createChat(buyerId, shop.getId(), null));
         if (input.partId() != null) {
            attachPart(chat, buyerId, input.partId());
         }
      }
      return view.chat(chat, ChatSide.BUYER, lang);
   }

   /**
    * Бокс ответил «Есть» (12): чат по запросу создаётся, если его ещё нет, и первым сообщением
    * в нём — карточка ответа. Вызывается в транзакции ответа. Возвращает id чата.
    */
   @Transactional(propagation = Propagation.MANDATORY)
   public Long openForReply(PartRequest request, RequestReply reply, Long authorId) {
      Chat chat = chats.findByBuyerIdAndShopIdAndRequestId(request.getBuyerId(), reply.getShopId(), request.getId())
            .orElseGet(() -> createChat(request.getBuyerId(), reply.getShopId(), request.getId()));
      Map<String, Object> card = new LinkedHashMap<>();
      card.put("replyId", reply.getId());
      card.put("condition", reply.getCondition() == null ? null : reply.getCondition().name());
      card.put("price", reply.getPrice());
      card.put("partId", reply.getPartId());
      // о «Есть» покупателю уже пришёл пуш модуля запросов — второй не нужен
      add(chat, Message.reply(chat.getId(), authorId, reply.getMessage(), card, clock.instant()), false);
      return chat.getId();
   }

   /**
    * Карточка товара в чате (ТЗ 5.3). Та же карточка подряд второй раз не добавляется — повторное
    * «Написать» с той же карточки просто открывает чат.
    */
   private void attachPart(Chat chat, Long buyerId, Long partId) {
      Part part = parts.findById(partId)
            .filter(found -> found.getShopId().equals(chat.getShopId()) && found.isActive())
            .orElseThrow(() -> new NotFoundException("PART_NOT_FOUND", "Запчасть не найдена"));
      if (chat.getLastMessageId() != null) {
         Message last = messages.findById(chat.getLastMessageId()).orElse(null);
         if (last != null && last.getType() == MessageType.PART && last.getPayload() != null
               && partId.equals(((Number) last.getPayload().get("partId")).longValue())) {
            return;
         }
      }
      Map<String, Object> card = new LinkedHashMap<>();
      card.put("partId", part.getId());
      card.put("price", part.getPrice());
      card.put("mediaId", part.getPhotoIds().isEmpty() ? null : part.getPhotoIds().get(0));
      add(chat, Message.part(chat.getId(), buyerId, part.getTitle(), card, clock.instant()), true);
   }

   /** Новый чат начинается с плашки «Оплата в боксе при осмотре». Гонку двух созданий ловит UNIQUE. */
   private Chat createChat(Long buyerId, Long shopId, Long requestId) {
      Instant now = clock.instant();
      Chat chat;
      try {
         chat = chats.saveAndFlush(new Chat(buyerId, shopId, requestId, now));
      } catch (DataIntegrityViolationException parallel) {
         return (requestId == null ? chats.findDirect(buyerId, shopId)
               : chats.findByBuyerIdAndShopIdAndRequestId(buyerId, shopId, requestId)).orElseThrow();
      }
      // плашка не делает чат «начатым»: last_message остаётся пустым до первого настоящего сообщения
      messages.save(Message.system(chat.getId(), SystemEvent.PAY_AT_BOX, null, now));
      return chat;
   }

   /** Покупатель закрыл запрос с боксом — в их чате плашка «Запрос закрыт». */
   @EventListener
   public void onRequestClosed(RequestEvents.Closed event) {
      if (event.shopId() == null) {
         return;
      }
      PartRequest request = requests.findById(event.requestId()).orElseThrow();
      chats.findByBuyerIdAndShopIdAndRequestId(request.getBuyerId(), event.shopId(), request.getId())
            .ifPresent(chat -> {
               Map<String, Object> payload = new LinkedHashMap<>();
               payload.put("stars", event.stars());
               add(chat, Message.system(chat.getId(), SystemEvent.REQUEST_CLOSED, payload, clock.instant()), false);
            });
   }

   // ─────────────────────── просмотр ───────────────────────

   /** Список чатов (16). as = BUYER — мои чаты покупателя, SHOP — чаты моего бокса. */
   @Transactional(readOnly = true)
   public CursorPage<ChatListItemDto> list(Long userId, ChatSide as, String cursor, Integer limit, Lang lang) {
      int size = CursorPage.limit(limit);
      Instant at = LIST_START;
      long beforeId = Long.MAX_VALUE;
      if (cursor != null && !cursor.isBlank()) {
         String[] parts = CursorPage.decode(cursor).split("\\|");
         try {
            at = Instant.parse(parts[0]);
            beforeId = Long.parseLong(parts[1]);
         } catch (RuntimeException e) {
            throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
         }
      }
      List<Chat> rows = as == ChatSide.SHOP
            ? chats.findShopPage(shopAccess.requireMember(userId).shop().getId(), at, beforeId, size + 1)
            : chats.findBuyerPage(userId, at, beforeId, size + 1);
      boolean more = rows.size() > size;
      List<Chat> page = more ? rows.subList(0, size) : rows;
      String next = null;
      if (more) {
         Chat last = page.get(page.size() - 1);
         Instant key = last.getLastMessageAt() == null ? last.getCreatedAt() : last.getLastMessageAt();
         next = CursorPage.encode(key + "|" + last.getId());
      }
      return new CursorPage<>(view.rows(page, as, lang), next);
   }

   @Transactional(readOnly = true)
   public ChatDto get(Long userId, Long chatId, Lang lang) {
      ChatAccess.Participant participant = access.require(userId, chatId);
      return view.chat(participant.chat(), participant.side(), lang);
   }

   /** История: новые первыми; cursor — от самого старого загруженного сообщения вглубь. */
   @Transactional(readOnly = true)
   public CursorPage<MessageDto> messages(Long userId, Long chatId, String cursor, Integer limit) {
      Chat chat = access.require(userId, chatId).chat();
      int size = CursorPage.limit(limit);
      long beforeId = cursor == null || cursor.isBlank() ? Long.MAX_VALUE : CursorPage.afterId(cursor);
      List<Message> rows = messages.findPage(chatId, beforeId, PageRequest.of(0, size + 1));
      return CursorPage.of(rows, size, message -> String.valueOf(message.getId()),
            message -> view.message(message, chat));
   }

   @Transactional(readOnly = true)
   public UnreadDto unread(Long userId) {
      Long shopId = view.shopIdOf(userId);
      return new UnreadDto(chats.totalUnreadForBuyer(userId), shopId == null ? 0 : chats.totalUnreadForShop(shopId));
   }

   /** Токен подписки на канал чата — только участнику. */
   @Transactional(readOnly = true)
   public CentrifugoTokens.Token subscriptionToken(Long userId, Long chatId) {
      access.require(userId, chatId);
      return tokens.subscription(userId, ChatChannels.chat(chatId));
   }

   // ─────────────────────── отправка ───────────────────────

   /** Текст или быстрый ответ. Повтор с тем же clientId возвращает уже сохранённое сообщение. */
   @Transactional
   public MessageDto send(Long userId, Long chatId, ChatInputs.SendMessage input) {
      ChatAccess.Participant participant = access.requireForUpdate(userId, chatId);
      Chat chat = participant.chat();
      Optional<Message> repeated = repeated(chat, input.clientId());
      if (repeated.isPresent()) {
         return view.message(repeated.get(), chat);
      }
      requireWritable(chat);
      Instant now = clock.instant();
      Message message;
      if (input.quickReply() != null) {
         message = quick(participant, input.quickReply(), input.clientId(), now);
      } else {
         if (input.text() == null || input.text().isBlank()) {
            throw new BadRequestException("TEXT_REQUIRED", "Сообщение не может быть пустым");
         }
         message = Message.text(chatId, participant.side(), userId, input.text().trim(), input.clientId(), now);
      }
      add(chat, message, true);
      return view.message(message, chat);
   }

   /**
    * Быстрый ответ своей стороны (ТЗ 11.1). Текст — на языке отправителя, как если бы он написал сам.
    * ROUTE несёт место бокса для карточки «Маршрут»; ACTION-кнопки сообщением не отправляются.
    */
   private Message quick(ChatAccess.Participant participant, QuickReply reply, String clientId, Instant now) {
      if (reply.side() != participant.side() || reply.kind() != QuickReply.Kind.MESSAGE) {
         throw new BadRequestException("QUICK_REPLY_NOT_ALLOWED", "Этот быстрый ответ здесь недоступен");
      }
      Chat chat = participant.chat();
      Lang lang = users.findById(participant.userId()).map(user -> user.getLang()).orElse(Lang.RU);
      Map<String, Object> payload = null;
      if (reply == QuickReply.ROUTE) {
         Shop shop = shops.findById(chat.getShopId()).orElseThrow();
         payload = new LinkedHashMap<>();
         payload.put("location", json.convertValue(shopMapper.location(shop.getContainerId()),
               new TypeReference<Map<String, Object>>() {
               }));
      }
      return Message.quick(chat.getId(), participant.side(), participant.userId(), reply, reply.label(lang), payload,
            clientId, now);
   }

   /**
    * Фото, голосовое (до 60 секунд) или видео. Файл загружается в хранилище вне транзакции, чтобы
    * не держать соединение с базой, пока он летит в MinIO.
    */
   public MessageDto sendMedia(Long userId, Long chatId, MultipartFile file, MessageType type, String caption,
                               Integer durationSeconds, String waveformJson, String clientId) {
      if (!type.isMedia()) {
         throw new BadRequestException("BAD_MEDIA_TYPE", "Тип вложения: PHOTO, VOICE или VIDEO");
      }
      if (type == MessageType.VOICE && durationSeconds != null && durationSeconds > mediaConfig.maxVoiceSeconds()) {
         throw new BadRequestException("VOICE_TOO_LONG",
               "Голосовое — не длиннее " + mediaConfig.maxVoiceSeconds() + " секунд");
      }
      List<Double> waveform = type == MessageType.VOICE ? waveform(waveformJson) : null;
      MessageDto existing = tx.execute(status -> {
         ChatAccess.Participant participant = access.require(userId, chatId);
         requireWritable(participant.chat());
         return repeated(participant.chat(), clientId).map(m -> view.message(m, participant.chat())).orElse(null);
      });
      if (existing != null) {
         return existing;
      }
      ChatAttachments.Stored stored = media.store(file, type);
      return tx.execute(status -> {
         ChatAccess.Participant participant = access.requireForUpdate(userId, chatId);
         Chat chat = participant.chat();
         Optional<Message> repeated = repeated(chat, clientId);
         if (repeated.isPresent()) {
            return view.message(repeated.get(), chat);
         }
         requireWritable(chat);
         String text = caption == null || caption.isBlank() ? null : caption.trim();
         Message message = Message.media(chatId, participant.side(), userId, type, text, stored.key(),
               stored.mimeType(), durationSeconds, waveform, clientId, clock.instant());
         add(chat, message, true);
         return view.message(message, chat);
      });
   }

   /** Сохранить, сдвинуть «последнее сообщение», продлить жизнь запроса, после коммита — доставить. */
   private void add(Chat chat, Message message, boolean push) {
      messages.save(message);
      if (message.getSide() != ChatSide.SYSTEM) {
         chat.messageAdded(message);
      }
      if (chat.getRequestId() != null && message.getSide() != ChatSide.SYSTEM) {
         requests.findById(chat.getRequestId()).filter(PartRequest::isOpen)
               .ifPresent(request -> request.touch(message.getCreatedAt()));
      }
      events.publishEvent(new ChatEvents.MessageAdded(chat.getId(), message.getId(), push));
   }

   private Optional<Message> repeated(Chat chat, String clientId) {
      return clientId == null || clientId.isBlank() ? Optional.empty()
            : messages.findByChatIdAndClientId(chat.getId(), clientId);
   }

   private void requireWritable(Chat chat) {
      if (chat.isBlocked()) {
         throw new ForbiddenException("CHAT_BLOCKED", "Переписка заблокирована");
      }
      Shop shop = shops.findById(chat.getShopId()).orElseThrow();
      if (!shop.isActive()) {
         throw new ForbiddenException("SHOP_NOT_ACTIVE", "Магазин не принимает сообщения");
      }
   }

   /** JSON-массив до 100 пиков громкости 0..1; пусто — без волны. */
   private List<Double> waveform(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      List<Double> peaks;
      try {
         peaks = json.readValue(raw, new TypeReference<List<Double>>() {
         });
      } catch (Exception e) {
         throw new BadRequestException("BAD_WAVEFORM", "waveform — JSON-массив чисел");
      }
      if (peaks.isEmpty() || peaks.size() > MAX_WAVEFORM_POINTS
            || peaks.stream().anyMatch(peak -> peak == null || peak.isNaN() || peak < 0 || peak > 1)) {
         throw new BadRequestException("BAD_WAVEFORM", "waveform — от 1 до 100 чисел от 0 до 1");
      }
      return new ArrayList<>(peaks);
   }

   // ─────────────────────── прочтение, блокировка, жалоба ───────────────────────

   /** «Прочитано» до сообщения (по умолчанию — до последнего) за всю свою сторону. */
   @Transactional
   public void read(Long userId, Long chatId, Long upToMessageId) {
      ChatAccess.Participant participant = access.requireForUpdate(userId, chatId);
      Chat chat = participant.chat();
      long last = chat.getLastMessageId() == null ? 0 : chat.getLastMessageId();
      long upTo = upToMessageId == null ? last : Math.min(upToMessageId, last);
      if (chat.read(participant.side(), upTo)) {
         events.publishEvent(new ChatEvents.Read(chatId, participant.side(), upTo));
      }
   }

   /** «Заблокировать собеседника»: писать не может никто, пока блокирующая сторона не снимет блок. */
   @Transactional
   public ChatDto block(Long userId, Long chatId, boolean blocked, Lang lang) {
      ChatAccess.Participant participant = access.requireForUpdate(userId, chatId);
      participant.chat().setBlocked(participant.side(), blocked);
      events.publishEvent(new ChatEvents.Changed(chatId));
      return view.chat(participant.chat(), participant.side(), lang);
   }

   @Transactional
   public ComplaintDto complain(Long userId, Long chatId, String text) {
      access.require(userId, chatId);
      return complaints.create(userId, ComplaintType.CHAT, chatId, text);
   }

   // ─────────────────────── быстрые ответы ───────────────────────

   /**
    * Кнопки над полем ввода (ТЗ 11.1). Покупателю — «Как пройти к боксу» и, пока запрос открыт,
    * «Купил — закрыть запрос». Продавцу — «Отложил для вас», «Как пройти», «Продано» и свои шаблоны бокса.
    */
   @Transactional(readOnly = true)
   public List<QuickReplyDto> quickReplies(Long userId, Long chatId, Lang lang) {
      ChatAccess.Participant participant = access.require(userId, chatId);
      Chat chat = participant.chat();
      List<QuickReplyDto> result = new ArrayList<>();
      if (participant.side() == ChatSide.BUYER) {
         result.add(quick(QuickReply.ROUTE_TO_BOX, lang));
         boolean requestOpen = chat.getRequestId() != null
               && requests.findById(chat.getRequestId()).map(PartRequest::isOpen).orElse(false);
         if (requestOpen) {
            result.add(quick(QuickReply.CLOSE_REQUEST, lang));
         }
         return result;
      }
      result.add(quick(QuickReply.RESERVED, lang));
      result.add(quick(QuickReply.ROUTE, lang));
      result.add(quick(QuickReply.SOLD, lang));
      templates.findByShopIdOrderBySortOrderAscIdAsc(chat.getShopId()).forEach(template ->
            result.add(new QuickReplyDto(null, QuickReply.Kind.MESSAGE, template.getId(), template.getText())));
      return result;
   }

   private static QuickReplyDto quick(QuickReply reply, Lang lang) {
      return new QuickReplyDto(reply, reply.kind(), null, reply.label(lang));
   }
}
