package kg.kudaibergen.request;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.CatalogView;
import kg.kudaibergen.catalog.MyPartsService;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.chat.ChatRepository;
import kg.kudaibergen.chat.ChatService;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.request.dto.IncomingRequestDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Входящие запросы бокса (ТЗ 10): общая лента владельца и сотрудников, ответ «Есть» / «Нет» из списка
 * или из пуша. Первый ответ засчитывается за магазин, второй невозможен; изменить можно 10 минут.
 */
@Service
public class IncomingRequestService {

   /** Первая страница ленты: всё, что раньше «бесконечности». */
   private static final Instant FEED_START = Instant.parse("9999-12-31T00:00:00Z");

   private final PartRequestRepository requests;
   private final RequestRecipientRepository recipients;
   private final RequestReplyRepository replies;
   private final ShopAccess access;
   private final ShopMapper shopMapper;
   private final RequestMapper mapper;
   private final UserRepository users;
   private final ChatService chatService;
   private final ChatRepository chats;
   private final MyPartsService myParts;
   private final CatalogView catalogView;
   private final ApplicationEventPublisher events;
   private final AppProperties.Requests config;
   private final Clock clock;

   public IncomingRequestService(PartRequestRepository requests, RequestRecipientRepository recipients,
                                 RequestReplyRepository replies, ShopAccess access, ShopMapper shopMapper,
                                 RequestMapper mapper, UserRepository users, ChatService chatService,
                                 ChatRepository chats, MyPartsService myParts, CatalogView catalogView,
                                 ApplicationEventPublisher events,
                                 AppProperties properties, Clock clock) {
      this.requests = requests;
      this.recipients = recipients;
      this.replies = replies;
      this.access = access;
      this.shopMapper = shopMapper;
      this.mapper = mapper;
      this.users = users;
      this.chatService = chatService;
      this.chats = chats;
      this.myParts = myParts;
      this.catalogView = catalogView;
      this.events = events;
      this.config = properties.requests();
      this.clock = clock;
   }

   /** Какую часть ленты показать (11): новые, «Вы ответили «есть»», без ответа (из статистики 17). */
   public enum Filter {
      NEW,
      ANSWERED,
      UNANSWERED
   }

   // ─────────────────────── лента ───────────────────────

   @Transactional(readOnly = true)
   public CursorPage<IncomingRequestDto> feed(Long userId, Filter filter, String cursor, Integer limit, Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      int size = CursorPage.limit(limit);
      Instant at = FEED_START;
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
      List<RequestRecipient> rows = switch (filter) {
         case NEW -> recipients.findNew(shop.getId(), at, beforeId, size + 1);
         case ANSWERED -> recipients.findAnswered(shop.getId(), at, beforeId, size + 1);
         case UNANSWERED -> recipients.findUnanswered(shop.getId(), at, beforeId, size + 1);
      };
      List<Long> ids = rows.stream().map(RequestRecipient::getRequestId).toList();
      Map<Long, PartRequest> byId = requests.findAllById(ids).stream()
            .collect(Collectors.toMap(PartRequest::getId, Function.identity()));
      Map<Long, RequestReply> myReplies = replies.findByShopIdAndRequestIdIn(shop.getId(), ids).stream()
            .collect(Collectors.toMap(RequestReply::getRequestId, Function.identity()));
      Map<Long, String> buyerNames = buyerNames(byId.values());
      Map<Long, Long> chatOfRequest = chats.findByShopIdAndRequestIdIn(shop.getId(), ids).stream()
            .collect(Collectors.toMap(Chat::getRequestId, Chat::getId, (a, b) -> a));
      return CursorPage.of(rows, size, row -> row.getNotifiedAt() + "|" + row.getRequestId(), row -> {
         PartRequest request = byId.get(row.getRequestId());
         RequestReply reply = myReplies.get(row.getRequestId());
         return mapper.incoming(request, row, buyerNames.get(request.getBuyerId()),
               reply == null ? null : mapper.reply(reply, null, chatOfRequest.get(row.getRequestId()),
                     partCard(reply, request)), lang);
      });
   }

   /** Шторка ответа (12) и переход из пуша (14). */
   @Transactional(readOnly = true)
   public IncomingRequestDto one(Long userId, Long requestId, Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      RequestRecipient recipient = recipient(requestId, shop.getId());
      PartRequest request = requests.findById(requestId).orElseThrow(RequestService::notFound);
      ReplyDto myReply = replies.findByRequestIdAndShopId(requestId, shop.getId())
            .map(reply -> mapper.reply(reply, null, chatId(request, shop.getId()), partCard(reply, request)))
            .orElse(null);
      return mapper.incoming(request, recipient, buyerNames(List.of(request)).get(request.getBuyerId()), myReply,
            lang);
   }

   /** Продавец открыл запрос — покупатель видит «видели N продавцов». */
   @Transactional
   public void seen(Long userId, Long requestId) {
      Shop shop = access.requireMember(userId).shop();
      recipient(requestId, shop.getId()).seen(clock.instant());
   }

   // ─────────────────────── ответ ───────────────────────

   /**
    * «Отправить «Есть»» (12) или «Нет» (11, 14). «Есть» — пуш покупателю, ответ появляется на экране 07;
    * «Нет» — запрос скрывается из ленты, покупатель ничего не получает.
    */
   @Transactional
   public ReplyDto reply(Long userId, Long requestId, RequestInputs.Reply input) {
      Shop shop = activeShop(userId);
      PartRequest request = requests.findForUpdate(requestId).orElseThrow(RequestService::notFound);
      RequestRecipient recipient = recipient(requestId, shop.getId());
      RequestService.requireOpen(request);
      if (replies.findByRequestIdAndShopId(requestId, shop.getId()).isPresent()) {
         throw new ConflictException("ALREADY_REPLIED", "Бокс уже ответил на этот запрос");
      }
      requireCondition(input);
      requirePart(shop, input);

      Instant now = clock.instant();
      RequestReply reply = new RequestReply(requestId, shop.getId(), userId, now);
      reply.fill(input.answer(), input.condition(), trimToNull(input.message()), input.price(), input.partId(), now);
      try {
         replies.saveAndFlush(reply);
      } catch (DataIntegrityViolationException parallel) {
         throw new ConflictException("ALREADY_REPLIED", "Бокс уже ответил на этот запрос");
      }
      recipient.replied(now);
      Long chatId = null;
      if (reply.isHave()) {
         request.haveCountChanged(1, now);
         // чат с карточкой ответа первым сообщением (ТЗ 10.2)
         chatId = chatService.openForReply(request, reply, userId);
         events.publishEvent(new RequestEvents.HaveReceived(requestId, reply.getId()));
      }
      return mapper.reply(reply, shopMapper.card(shop), chatId, partCard(reply, request));
   }

   /** Изменить ответ в течение 10 минут (ТЗ 10.2), в том числе «Нет» ↔ «Есть». Потом — только в чате. */
   @Transactional
   public ReplyDto editReply(Long userId, Long requestId, RequestInputs.Reply input) {
      Shop shop = activeShop(userId);
      PartRequest request = requests.findForUpdate(requestId).orElseThrow(RequestService::notFound);
      RequestReply reply = replies.findByRequestIdAndShopId(requestId, shop.getId())
            .orElseThrow(() -> new NotFoundException("REPLY_NOT_FOUND", "Бокс ещё не отвечал на этот запрос"));
      RequestService.requireOpen(request);
      Instant now = clock.instant();
      if (now.isAfter(reply.getCreatedAt().plus(config.replyEditWindow()))) {
         throw new ConflictException("REPLY_EDIT_EXPIRED", "Ответ можно изменить только в течение "
               + config.replyEditWindow().toMinutes() + " минут — напишите покупателю в чат");
      }
      requireCondition(input);
      requirePart(shop, input);
      boolean wasHave = reply.isHave();
      reply.fill(input.answer(), input.condition(), trimToNull(input.message()), input.price(), input.partId(), now);
      if (wasHave != reply.isHave()) {
         request.haveCountChanged(reply.isHave() ? 1 : -1, now);
      }
      if (!wasHave && reply.isHave()) {
         chatService.openForReply(request, reply, userId);
         events.publishEvent(new RequestEvents.HaveReceived(requestId, reply.getId()));
      }
      return mapper.reply(reply, shopMapper.card(shop), chatId(request, shop.getId()), partCard(reply, request));
   }

   /** Заблокированный или ещё не проверенный бокс не отвечает. */
   private Shop activeShop(Long userId) {
      Shop shop = access.requireMember(userId).shop();
      if (!shop.isActive()) {
         throw new ForbiddenException("SHOP_NOT_ACTIVE", "Бокс не проверен или заблокирован");
      }
      return shop;
   }

   /**
    * «Приложить товар из каталога» (12): свои опубликованные запчасти под машину запроса,
    * сначала совпавшие с текстом запроса.
    */
   @Transactional(readOnly = true)
   public List<PartCardDto> suggestedParts(Long userId, Long requestId) {
      Shop shop = access.requireMember(userId).shop();
      recipient(requestId, shop.getId());
      PartRequest request = requests.findById(requestId).orElseThrow(RequestService::notFound);
      return myParts.suggestions(shop.getId(), mapper.carFilter(request), request.getText());
   }

   private void requirePart(Shop shop, RequestInputs.Reply input) {
      if (input.partId() != null && input.answer() == ReplyAnswer.HAVE) {
         myParts.requireActiveOf(shop.getId(), input.partId());
      }
   }

   private PartCardDto partCard(RequestReply reply, PartRequest request) {
      if (reply.getPartId() == null) {
         return null;
      }
      return catalogView.cards(List.of(reply.getPartId()), mapper.carFilter(request), null).stream()
            .findFirst().orElse(null);
   }

   private Long chatId(PartRequest request, Long shopId) {
      return chats.findByBuyerIdAndShopIdAndRequestId(request.getBuyerId(), shopId, request.getId())
            .map(Chat::getId).orElse(null);
   }

   private RequestRecipient recipient(Long requestId, Long shopId) {
      return recipients.findByRequestIdAndShopId(requestId, shopId).orElseThrow(RequestService::notFound);
   }

   private static void requireCondition(RequestInputs.Reply input) {
      if (input.answer() == ReplyAnswer.HAVE && input.condition() == null) {
         throw new BadRequestException("CONDITION_REQUIRED", "Укажите состояние: новое, б/у или под заказ");
      }
   }

   private static String trimToNull(String text) {
      return text == null || text.isBlank() ? null : text.trim();
   }

   /** Имя покупателя для продавца (может быть не заполнено); телефон не показываем (ТЗ 14). */
   private Map<Long, String> buyerNames(Collection<PartRequest> list) {
      Map<Long, String> names = new HashMap<>();
      users.findAllById(list.stream().map(PartRequest::getBuyerId).distinct().toList())
            .forEach(user -> names.put(user.getId(), user.getName()));
      return names;
   }
}
