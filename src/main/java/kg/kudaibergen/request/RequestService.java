package kg.kudaibergen.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.chat.ChatRepository;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.GarageService;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.request.dto.RecipientsPreviewDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestDetailDto;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.dto.RequestSummaryDto;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.request.dto.WidenResultDto;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.request.entity.Review;
import kg.kudaibergen.request.entity.ReviewTag;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.entity.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запрос «Найти запчасть» со стороны покупателя (ТЗ 4.3–4.5): отправка продавцам марки, «Мои запросы»,
 * ответы «Есть», закрытие с оценкой, «Отправить всему рынку». Плюс таймеры «никто не ответил» и «истёк».
 */
@Service
public class RequestService {

   private static final Logger log = LoggerFactory.getLogger(RequestService.class);
   private static final Duration DAY = Duration.ofDays(1);
   private static final int JOB_BATCH = 200;

   private final PartRequestRepository requests;
   private final RequestRecipientRepository recipients;
   private final RequestReplyRepository replies;
   private final ReviewRepository reviews;
   private final ChatRepository chats;
   private final RecipientFinder finder;
   private final RequestMapper mapper;
   private final GarageService garage;
   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final MarketMapService market;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final ApplicationEventPublisher events;
   private final AppProperties.Requests config;
   private final Clock clock;

   public RequestService(PartRequestRepository requests, RequestRecipientRepository recipients,
                         RequestReplyRepository replies, ReviewRepository reviews, ChatRepository chats,
                         RecipientFinder finder,
                         RequestMapper mapper, GarageService garage, VehicleDirectory directory,
                         CategoryService categories, MarketMapService market, ShopRepository shops,
                         ShopMapper shopMapper, ApplicationEventPublisher events, AppProperties properties,
                         Clock clock) {
      this.requests = requests;
      this.recipients = recipients;
      this.replies = replies;
      this.reviews = reviews;
      this.chats = chats;
      this.finder = finder;
      this.mapper = mapper;
      this.garage = garage;
      this.directory = directory;
      this.categories = categories;
      this.market = market;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.events = events;
      this.config = properties.requests();
      this.clock = clock;
   }

   // ─────────────────────── отправка ───────────────────────

   /** «Запрос получат 43 продавца по Toyota» под выбором адресата (06). */
   @Transactional(readOnly = true)
   public RecipientsPreviewDto preview(Long buyerId, RequestInputs.RecipientsPreview input) {
      Long brandId = input.carId() != null ? garage.getOwned(input.carId(), buyerId).getBrand().getId()
            : input.brandId();
      if (brandId == null) {
         throw new BadRequestException("CAR_REQUIRED", "Выберите машину или марку");
      }
      String brandName = directory.brand(brandId).getName();
      requireTarget(input.target(), input.targetRowId(), input.targetShopId());
      int count = finder.find(buyerId, brandId, input.target(), input.targetRowId(), input.targetShopId()).size();
      return new RecipientsPreviewDto(count, brandName);
   }

   /** «Отправить» (06): запрос уходит всем подходящим боксам, им — пуш с кнопками «Есть / Нет». */
   @Transactional
   public RequestDetailDto create(Long buyerId, RequestInputs.CreateRequest input, Lang lang) {
      Car car = garage.getOwned(input.carId(), buyerId);
      requireTarget(input.target(), input.targetRowId(), input.targetShopId());
      if (input.categoryId() != null) {
         categories.requireExisting(Set.of(input.categoryId()));
      }
      Instant now = clock.instant();
      checkLimits(buyerId, now);

      PartRequest request = requests.save(new PartRequest(buyerId, car.getId(), car.getBrand().getId(),
            car.getModel().getId(), car.getYear(), input.text().trim(), input.categoryId(), input.target(),
            input.target() == RequestTarget.ROW ? input.targetRowId() : null,
            input.target() == RequestTarget.SHOP ? input.targetShopId() : null, now));
      dispatch(request, finder.find(buyerId, request.getBrandId(), input.target(), input.targetRowId(),
            input.targetShopId()), Set.of(), now);
      return mapper.detail(request, 0, lang);
   }

   /** «Отправить всему рынку» (20): добавляются боксы марки, которым запрос ещё не приходил. */
   @Transactional
   public WidenResultDto widen(AuthPrincipal principal, Long requestId) {
      PartRequest request = requests.findForUpdate(requestId)
            .filter(found -> found.getBuyerId().equals(principal.userId()))
            .orElseThrow(RequestService::notFound);
      requireOpen(request);
      Instant now = clock.instant();
      Set<Long> already = recipients.findShopIds(requestId);
      request.widenToMarket(now);
      int added = dispatch(request, finder.find(request.getBuyerId(), request.getBrandId(), RequestTarget.MARKET,
            null, null), already, now);
      return new WidenResultDto(added, request.getRecipientsCount());
   }

   private int dispatch(PartRequest request, List<Shop> found, Set<Long> already, Instant now) {
      List<Long> shopIds = found.stream().map(Shop::getId).filter(id -> !already.contains(id)).toList();
      recipients.saveAll(shopIds.stream().map(shopId -> new RequestRecipient(request.getId(), shopId, now)).toList());
      request.dispatched(shopIds.size(), now);
      events.publishEvent(new RequestEvents.Dispatched(request.getId(), shopIds));
      log.info("Запрос {} разослан {} боксам", request.getId(), shopIds.size());
      return shopIds.size();
   }

   private void requireTarget(RequestTarget target, Long rowId, Long shopId) {
      switch (target) {
         case MARKET -> {
         }
         case ROW -> {
            if (rowId == null) {
               throw new BadRequestException("ROW_REQUIRED", "Выберите ряд");
            }
            market.snapshot().row(rowId).orElseThrow(() -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден"));
         }
         case SHOP -> {
            if (shopId == null) {
               throw new BadRequestException("SHOP_REQUIRED", "Выберите бокс");
            }
            shops.findById(shopId).filter(Shop::isActive)
                  .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
         }
      }
   }

   /** Не больше 10 открытых запросов и 20 новых за сутки (ТЗ 4.3). */
   private void checkLimits(Long buyerId, Instant now) {
      if (requests.countByBuyerIdAndStatus(buyerId, RequestStatus.OPEN) >= config.maxOpen()) {
         throw new ConflictException("OPEN_REQUESTS_LIMIT",
               "У вас уже " + config.maxOpen() + " открытых запросов — закройте ненужные");
      }
      Instant dayAgo = now.minus(DAY);
      if (requests.countByBuyerIdAndCreatedAtAfter(buyerId, dayAgo) >= config.maxPerDay()) {
         long retryAfter = requests.findFirstByBuyerIdAndCreatedAtAfterOrderByCreatedAtAsc(buyerId, dayAgo)
               .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(DAY)).toSeconds() + 1)
               .orElse(DAY.toSeconds());
         throw new RateLimitException("DAILY_REQUESTS_LIMIT",
               "Не больше " + config.maxPerDay() + " запросов в сутки", Math.max(retryAfter, 1));
      }
   }

   // ─────────────────────── просмотр ───────────────────────

   /** «Мои запросы» (05): открытые сверху, потом закрытые и истёкшие. status — только один статус. */
   @Transactional(readOnly = true)
   public CursorPage<RequestSummaryDto> mine(Long buyerId, RequestStatus status, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      int rank = 0;
      long beforeId = Long.MAX_VALUE;
      if (cursor != null && !cursor.isBlank()) {
         String[] parts = CursorPage.decode(cursor).split(":");
         try {
            rank = Integer.parseInt(parts[0]);
            beforeId = Long.parseLong(parts[1]);
         } catch (RuntimeException e) {
            throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
         }
      }
      List<PartRequest> rows = requests.findMine(buyerId, status, RequestStatus.OPEN, rank, beforeId,
            PageRequest.of(0, size + 1));
      return CursorPage.of(rows, size, request -> (request.isOpen() ? 0 : 1) + ":" + request.getId(),
            mapper::summary);
   }

   @Transactional(readOnly = true)
   public RequestDetailDto detail(AuthPrincipal principal, Long requestId, Lang lang) {
      PartRequest request = visible(principal, requestId);
      return mapper.detail(request, recipients.countByRequestIdAndSeenAtIsNotNull(requestId), lang);
   }

   /**
    * Ответы «Есть» (07) в порядке прихода. «Нет» покупатель не видит.
    * afterId — id последнего полученного ответа: клиент опрашивает только новые.
    */
   @Transactional(readOnly = true)
   public List<ReplyDto> replies(AuthPrincipal principal, Long requestId, Long afterId) {
      visible(principal, requestId);
      List<RequestReply> have = replies.findHave(requestId, ReplyAnswer.HAVE, afterId == null ? 0 : afterId);
      Map<Long, Shop> byId = shops.findAllById(have.stream().map(RequestReply::getShopId).toList()).stream()
            .collect(Collectors.toMap(Shop::getId, Function.identity()));
      Map<Long, Long> chatOfShop = chats.findByRequestId(requestId).stream()
            .collect(Collectors.toMap(Chat::getShopId, Chat::getId, (a, b) -> a));
      return have.stream()
            .map(reply -> mapper.reply(reply, shopMapper.card(byId.get(reply.getShopId())),
                  chatOfShop.get(reply.getShopId())))
            .toList();
   }

   public List<ReviewTagDto> reviewTags(Lang lang) {
      return Arrays.stream(ReviewTag.values()).map(tag -> new ReviewTagDto(tag, tag.label(lang))).toList();
   }

   /** Свой запрос видит покупатель, любой — суперадмин (ТЗ, раздел 2). Остальным — 404. */
   private PartRequest visible(AuthPrincipal principal, Long requestId) {
      return requests.findById(requestId)
            .filter(request -> request.getBuyerId().equals(principal.userId()) || principal.isSuperadmin())
            .orElseThrow(RequestService::notFound);
   }

   // ─────────────────────── закрытие ───────────────────────

   /**
    * «Купил — закрыть запрос» (09) или «Закрыть запрос» (20). С боксом — только с тем, кто ответил «Есть»;
    * оценка ставится этому боксу и сразу пересчитывает его рейтинг. Остальные боксы запрос больше не видят.
    */
   @Transactional
   public RequestDetailDto close(AuthPrincipal principal, Long requestId, RequestInputs.CloseRequest input,
                                 Lang lang) {
      PartRequest request = requests.findForUpdate(requestId)
            .filter(found -> found.getBuyerId().equals(principal.userId()) || principal.isSuperadmin())
            .orElseThrow(RequestService::notFound);
      requireOpen(request);
      boolean byBuyer = request.getBuyerId().equals(principal.userId());
      Long shopId = input.shopId();
      if (input.stars() != null && (shopId == null || !byBuyer)) {
         throw new BadRequestException("SHOP_REQUIRED", "Оценку можно поставить только боксу, у которого купили");
      }
      if (shopId != null && !replies.existsByRequestIdAndShopIdAndAnswer(requestId, shopId, ReplyAnswer.HAVE)) {
         throw new BadRequestException("SHOP_NOT_REPLIED", "Этот бокс не отвечал «Есть» на запрос");
      }
      Instant now = clock.instant();
      request.close(shopId, now);
      if (input.stars() != null) {
         List<ReviewTag> tags = input.tags() == null ? List.of() : List.copyOf(new HashSet<>(input.tags()));
         reviews.saveAndFlush(new Review(shopId, principal.userId(), requestId, input.stars(), tags, now));
         recalculateRating(shopId);
      }
      events.publishEvent(new RequestEvents.Closed(requestId, shopId, input.stars()));
      return mapper.detail(request, recipients.countByRequestIdAndSeenAtIsNotNull(requestId), lang);
   }

   private void recalculateRating(Long shopId) {
      Shop shop = shops.findById(shopId).orElseThrow();
      Double average = reviews.averageStars(shopId);
      BigDecimal rating = average == null ? BigDecimal.ZERO
            : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
      shop.updateRating(rating, (int) reviews.countByShopId(shopId));
   }

   // ─────────────────────── таймеры ───────────────────────

   /** 30 минут без «Есть» — пуш покупателю и экран «Пока никто не ответил» (20). Возвращает число запросов. */
   @Transactional
   public int markNoReply() {
      Instant now = clock.instant();
      List<PartRequest> due = requests.findNoReplyDue(RequestStatus.OPEN, now.minus(config.noReplyAfter()),
            PageRequest.of(0, JOB_BATCH));
      for (PartRequest request : due) {
         request.markNoReply(now);
         events.publishEvent(new RequestEvents.NoReply(request.getId()));
      }
      return due.size();
   }

   /** 7 дней без действий — EXPIRED: у продавцов запрос пропадает из ленты. */
   @Transactional
   public int expireIdle() {
      Instant now = clock.instant();
      List<PartRequest> idle = requests.findIdle(RequestStatus.OPEN, now.minus(config.expireAfter()),
            PageRequest.of(0, JOB_BATCH));
      idle.forEach(request -> request.expire(now));
      return idle.size();
   }

   static void requireOpen(PartRequest request) {
      if (!request.isOpen()) {
         throw new ConflictException("REQUEST_CLOSED", "Запрос уже закрыт");
      }
   }

   static NotFoundException notFound() {
      return new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден");
   }
}
