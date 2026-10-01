package kg.kudaibergen.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.CatalogView;
import kg.kudaibergen.catalog.dto.PartCardDto;
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
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.request.dto.PartHintDto;
import kg.kudaibergen.request.dto.RecipientsEstimateDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestDetailDto;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.dto.RequestStatsDto;
import kg.kudaibergen.request.dto.RequestSummaryDto;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.request.dto.WidenResultDto;
import kg.kudaibergen.request.entity.PartHint;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestDuration;
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
 * Запрос «Найти запчасть» со стороны покупателя (ТЗ 4.3–4.5, спецификация 3.7а): отправка всему рынку,
 * рядам или контейнерам, «Мои запросы», ответы «Есть», статистика, закрытие с оценкой, продление и
 * «Отправить всему рынку». Плюс таймер: по expiresAt запрос становится EXPIRED.
 */
@Service
public class RequestService {

   private static final Logger log = LoggerFactory.getLogger(RequestService.class);
   private static final Duration DAY = Duration.ofDays(1);
   private static final int JOB_BATCH = 200;
   /** «Продлить» (32): на 30 минут, час или 3 часа. */
   static final Set<Integer> EXTEND_MINUTES = Set.of(30, 60, 180);
   /** «Колодки · Радиатор · Фара» — три чипа под полем (06). */
   static final int DEFAULT_HINTS = 3;
   static final int MAX_HINTS = 12;

   private final PartRequestRepository requests;
   private final RequestRecipientRepository recipients;
   private final RequestReplyRepository replies;
   private final ReviewRepository reviews;
   private final ChatRepository chats;
   private final CatalogView catalogView;
   private final RecipientFinder finder;
   private final RequestMapper mapper;
   private final GarageService garage;
   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final MarketMapService market;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final MediaService media;
   private final RequestStatsView statsView;
   private final PartHintRepository hints;
   private final ApplicationEventPublisher events;
   private final AppProperties.Requests config;
   private final Clock clock;

   public RequestService(PartRequestRepository requests, RequestRecipientRepository recipients,
                         RequestReplyRepository replies, ReviewRepository reviews, ChatRepository chats,
                         CatalogView catalogView, RecipientFinder finder,
                         RequestMapper mapper, GarageService garage, VehicleDirectory directory,
                         CategoryService categories, MarketMapService market, ShopRepository shops,
                         ShopMapper shopMapper, MediaService media, RequestStatsView statsView,
                         PartHintRepository hints, ApplicationEventPublisher events, AppProperties properties, Clock clock) {
      this.requests = requests;
      this.recipients = recipients;
      this.replies = replies;
      this.reviews = reviews;
      this.chats = chats;
      this.catalogView = catalogView;
      this.finder = finder;
      this.mapper = mapper;
      this.garage = garage;
      this.directory = directory;
      this.categories = categories;
      this.market = market;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.media = media;
      this.statsView = statsView;
      this.hints = hints;
      this.events = events;
      this.config = properties.requests();
      this.clock = clock;
   }

   // ─────────────────────── отправка ───────────────────────

   /** Адресаты после проверки: ряды и контейнеры без повторов, пустые списки для «Всему рынку». */
   record Targets(RequestTarget target, List<Long> rowIds, List<Long> containerIds) {
   }

   /**
    * «Запрос получат 18 продавцов по Toyota» (06б, 31): пересчитывается при каждом изменении выбора.
    * Машина из гаража или просто марка (без гаража).
    */
   @Transactional(readOnly = true)
   public RecipientsEstimateDto estimate(Long buyerId, Long carId, Long brandId, RequestTarget target,
                                         List<Long> rowIds, List<Long> containerIds) {
      Long brand = carId != null ? garage.getOwned(carId, buyerId).getBrand().getId() : brandId;
      if (brand == null) {
         throw new BadRequestException("CAR_REQUIRED", "Выберите машину или марку");
      }
      String brandName = directory.brand(brand).getName();
      Targets targets = targets(target, rowIds, containerIds);
      int count = finder.find(buyerId, brand, targets.target(), targets.rowIds(), targets.containerIds()).size();
      return new RecipientsEstimateDto(count, brandName);
   }

   /** «Отправить» (06): запрос уходит всем подходящим боксам, им — пуш с кнопками «Есть / Нет». */
   @Transactional
   public RequestDetailDto create(Long buyerId, RequestInputs.CreateRequest input, Lang lang) {
      Car car = garage.getOwned(input.carId(), buyerId);
      Targets targets = targets(input.target(), input.targetRowIds(), input.targetContainerIds());
      PartHint hint = input.hintId() == null ? null : hints.findById(input.hintId())
            .orElseThrow(() -> new NotFoundException("HINT_NOT_FOUND", "Подсказка не найдена"));
      Long categoryId = input.categoryId() != null ? input.categoryId() : hint == null ? null : hint.getCategoryId();
      if (categoryId != null) {
         categories.requireExisting(Set.of(categoryId));
      }
      List<Long> photoIds = input.mediaIds() == null ? List.of() : input.mediaIds().stream().distinct().toList();
      if (!photoIds.isEmpty()) {
         media.requireUsable(photoIds, List.of(buyerId), Set.of(MediaPurpose.REQUEST));
      }
      Instant now = clock.instant();
      checkLimits(buyerId, now);
      List<Shop> found = finder.find(buyerId, car.getBrand().getId(), targets.target(), targets.rowIds(),
            targets.containerIds());
      if (found.isEmpty()) {
         // запрос, который никто не увидит, только занимает лимит и ждёт впустую — клиент предлагает другой выбор
         throw new ConflictException("NO_RECIPIENTS", noRecipientsMessage(targets.target()));
      }

      RequestDuration duration = input.duration() == null ? RequestDuration.MIN_30 : input.duration();
      PartRequest request = requests.save(new PartRequest(buyerId, car.getId(), car.getBrand().getId(),
            car.getModel().getId(), car.getYear(), input.text().trim(), categoryId, input.hintId(), targets.target(),
            targets.rowIds(), targets.containerIds(), photoIds, duration, now));
      dispatch(request, found, Set.of(), now);
      return mapper.detail(request, 0, lang);
   }

   /** Почему запрос некому отправить — текст для показа. */
   private static String noRecipientsMessage(RequestTarget target) {
      return switch (target) {
         case MARKET -> "Сейчас запрос никому не уйдёт: боксы с вашей маркой закрыты. Попробуйте в рабочее время";
         case ROWS -> "В выбранных рядах сейчас нет открытых боксов с вашей маркой — выберите другие ряды или весь рынок";
         case CONTAINERS -> "Выбранные боксы сейчас закрыты — выберите другие или отправьте всему рынку";
      };
   }

   /**
    * «Отправить всему рынку» (20, 32): добавляются боксы марки, которым запрос ещё не приходил, им — пуш;
    * срок отсчитывается заново, истёкший запрос снова активен.
    */
   @Transactional
   public WidenResultDto widen(AuthPrincipal principal, Long requestId, RequestInputs.Widen input) {
      if (input != null && input.target() != null && input.target() != RequestTarget.MARKET) {
         throw new BadRequestException("WIDEN_TARGET", "Расширить запрос можно только до всего рынка");
      }
      return widenToMarket(ownForUpdate(principal, requestId));
   }

   /** Админка [A8]: «Расширить» до всего рынка за покупателя — та же рассылка новым продавцам. */
   @Transactional
   public WidenResultDto adminWiden(Long requestId) {
      return widenToMarket(requests.findForUpdate(requestId).filter(found -> !found.isHiddenByAdmin())
            .orElseThrow(RequestService::notFound));
   }

   private WidenResultDto widenToMarket(PartRequest request) {
      Long requestId = request.getId();
      requireOpen(request);
      Instant now = clock.instant();
      reactivate(request, now);
      Set<Long> already = recipients.findShopIds(requestId);
      request.widenToMarket(now);
      int added = dispatch(request, finder.find(request.getBuyerId(), request.getBrandId(), RequestTarget.MARKET,
            List.of(), List.of()), already, now);
      return new WidenResultDto(added, request.getRecipientsCount(), request.getExpiresAt());
   }

   /** «Продлить» (32) на 30, 60 или 180 минут — до 3 раз. Истёкший запрос снова активен. */
   @Transactional
   public RequestDetailDto extend(AuthPrincipal principal, Long requestId, RequestInputs.Extend input, Lang lang) {
      if (!EXTEND_MINUTES.contains(input.minutes())) {
         throw new BadRequestException("BAD_EXTEND", "Продлить можно на 30, 60 или 180 минут");
      }
      PartRequest request = ownForUpdate(principal, requestId);
      requireOpen(request);
      if (!request.canExtend()) {
         throw new ConflictException("EXTEND_LIMIT",
               "Продлить можно не больше " + PartRequest.MAX_EXTENSIONS + " раз — отправьте запрос заново");
      }
      Instant now = clock.instant();
      reactivate(request, now);
      request.extend(Duration.ofMinutes(input.minutes()), now);
      events.publishEvent(new RequestEvents.StatsChanged(requestId));
      return mapper.detail(request, recipients.countByRequestIdAndSeenAtIsNotNull(requestId), lang);
   }

   /** Истёкший запрос снова активен: проверка лимита, продавцы без ответа снова его видят. */
   private void reactivate(PartRequest request, Instant now) {
      if (request.isActive()) {
         return;
      }
      checkActiveLimit(request.getBuyerId());
      recipients.revive(request.getId());
   }

   private int dispatch(PartRequest request, List<Shop> found, Set<Long> already, Instant now) {
      MarketSnapshot snapshot = market.snapshot();
      List<RequestRecipient> added = found.stream()
            .filter(shop -> !already.contains(shop.getId()))
            .map(shop -> {
               Long rowId = snapshot.container(shop.getContainerId())
                     .map(container -> container.row().row().getId()).orElse(null);
               return new RequestRecipient(request.getId(), shop.getId(), rowId, shop.getContainerId(), now);
            })
            .toList();
      recipients.saveAll(added);
      List<Long> shopIds = added.stream().map(RequestRecipient::getShopId).toList();
      request.dispatched(shopIds.size());
      events.publishEvent(new RequestEvents.Dispatched(request.getId(), shopIds));
      events.publishEvent(new RequestEvents.StatsChanged(request.getId()));
      log.info("Запрос {} разослан {} боксам", request.getId(), shopIds.size());
      return shopIds.size();
   }

   /** «Рядам» — 1–10 рядов, «Контейнерам» — 1–30 контейнеров; все должны быть на текущей схеме рынка. */
   private Targets targets(RequestTarget target, Collection<Long> rowIds, Collection<Long> containerIds) {
      MarketSnapshot snapshot = market.snapshot();
      return switch (target) {
         case MARKET -> new Targets(target, List.of(), List.of());
         case ROWS -> {
            List<Long> rows = distinct(rowIds);
            if (rows.isEmpty()) {
               throw new BadRequestException("ROWS_REQUIRED", "Выберите ряды");
            }
            if (rows.size() > RequestInputs.MAX_ROWS) {
               throw new BadRequestException("TOO_MANY_ROWS", "Не больше " + RequestInputs.MAX_ROWS + " рядов");
            }
            if (!rows.stream().allMatch(id -> snapshot.row(id).isPresent())) {
               throw new NotFoundException("ROW_NOT_FOUND", "Ряд не найден");
            }
            yield new Targets(target, rows, List.of());
         }
         case CONTAINERS -> {
            List<Long> containers = distinct(containerIds);
            if (containers.isEmpty()) {
               throw new BadRequestException("CONTAINERS_REQUIRED", "Выберите контейнеры");
            }
            if (containers.size() > RequestInputs.MAX_CONTAINERS) {
               throw new BadRequestException("TOO_MANY_CONTAINERS",
                     "Не больше " + RequestInputs.MAX_CONTAINERS + " контейнеров");
            }
            if (!containers.stream().allMatch(id -> snapshot.container(id).isPresent())) {
               throw new NotFoundException("CONTAINER_NOT_FOUND", "Контейнер не найден");
            }
            yield new Targets(target, List.of(), containers);
         }
      };
   }

   private static List<Long> distinct(Collection<Long> ids) {
      return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
   }

   /** Не больше 10 активных запросов и 20 новых за сутки (ТЗ 4.3). */
   private void checkLimits(Long buyerId, Instant now) {
      checkActiveLimit(buyerId);
      Instant dayAgo = now.minus(DAY);
      if (requests.countByBuyerIdAndCreatedAtAfter(buyerId, dayAgo) >= config.maxPerDay()) {
         long retryAfter = requests.findFirstByBuyerIdAndCreatedAtAfterOrderByCreatedAtAsc(buyerId, dayAgo)
               .map(oldest -> Duration.between(now, oldest.getCreatedAt().plus(DAY)).toSeconds() + 1)
               .orElse(DAY.toSeconds());
         throw new RateLimitException("DAILY_REQUESTS_LIMIT",
               "Не больше " + config.maxPerDay() + " запросов в сутки", Math.max(retryAfter, 1));
      }
   }

   private void checkActiveLimit(Long buyerId) {
      if (requests.countByBuyerIdAndStatus(buyerId, RequestStatus.ACTIVE) >= config.maxOpen()) {
         throw new ConflictException("OPEN_REQUESTS_LIMIT",
               "У вас уже " + config.maxOpen() + " активных запросов — закройте ненужные");
      }
   }

   // ─────────────────────── просмотр ───────────────────────

   /** «Мои запросы» (05): активные сверху, потом истёкшие и закрытые. status — только один статус. */
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
      List<PartRequest> rows = requests.findMine(buyerId, status, RequestStatus.ACTIVE, rank, beforeId,
            PageRequest.of(0, size + 1));
      return CursorPage.of(rows, size, request -> (request.isActive() ? 0 : 1) + ":" + request.getId(),
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
      PartRequest request = visible(principal, requestId);
      List<RequestReply> have = replies.findHave(requestId, ReplyAnswer.HAVE, afterId == null ? 0 : afterId);
      Map<Long, Shop> byId = shops.findAllById(have.stream().map(RequestReply::getShopId).toList()).stream()
            .collect(Collectors.toMap(Shop::getId, Function.identity()));
      Map<Long, Long> chatOfShop = chats.findByRequestId(requestId).stream()
            .collect(Collectors.toMap(Chat::getShopId, Chat::getId, (a, b) -> a));
      Map<Long, PartCardDto> partCards = catalogView.cards(have.stream().map(RequestReply::getPartId)
                  .filter(Objects::nonNull).distinct().toList(), mapper.carFilter(request), principal.userId())
            .stream().collect(Collectors.toMap(PartCardDto::id, Function.identity()));
      return have.stream()
            .map(reply -> mapper.reply(reply, shopMapper.card(byId.get(reply.getShopId())),
                  chatOfShop.get(reply.getShopId()), reply.getPartId() == null ? null : partCards.get(reply.getPartId())))
            .toList();
   }

   /** Статистика запроса (32): счётчики, «Есть» с магазинами, «Нет» — только места. */
   @Transactional(readOnly = true)
   public RequestStatsDto stats(AuthPrincipal principal, Long requestId) {
      return statsView.build(visible(principal, requestId));
   }

   /** Подсказки «что нужно» (06): топ для машины из гаража, без машины — самые частые вообще. */
   @Transactional(readOnly = true)
   public List<PartHintDto> hints(Long buyerId, Long carId, Integer limit, Lang lang) {
      long brandId = 0;
      long modelId = 0;
      if (carId != null) {
         Car car = garage.getOwned(carId, buyerId);
         brandId = car.getBrand().getId();
         modelId = car.getModel().getId();
      }
      int size = limit == null ? DEFAULT_HINTS : Math.clamp(limit, 1, MAX_HINTS);
      return hints.topFor(brandId, modelId, size).stream()
            .map(hint -> new PartHintDto(hint.getId(), hint.text(lang), hint.getCategoryId()))
            .toList();
   }

   public List<ReviewTagDto> reviewTags(Lang lang) {
      return Arrays.stream(ReviewTag.values()).map(tag -> new ReviewTagDto(tag, tag.label(lang))).toList();
   }

   /** Запрос видит только покупатель; админка смотрит запросы через свои эндпоинты. Остальным — 404. */
   private PartRequest visible(AuthPrincipal principal, Long requestId) {
      return requests.findById(requestId)
            .filter(request -> request.getBuyerId().equals(principal.userId()))
            .orElseThrow(RequestService::notFound);
   }

   private PartRequest ownForUpdate(AuthPrincipal principal, Long requestId) {
      return requests.findForUpdate(requestId)
            .filter(found -> found.getBuyerId().equals(principal.userId()))
            .orElseThrow(RequestService::notFound);
   }

   // ─────────────────────── закрытие ───────────────────────

   /**
    * «Купил — закрыть запрос» (09) или «Закрыть запрос» (20). Можно и после истечения времени.
    * С боксом — только с тем, кто ответил «Есть»; оценка ставится этому боксу и сразу пересчитывает
    * его рейтинг. Остальные боксы запрос больше не видят.
    */
   @Transactional
   public RequestDetailDto close(AuthPrincipal principal, Long requestId, RequestInputs.CloseRequest input,
                                 Lang lang) {
      PartRequest request = requests.findForUpdate(requestId)
            .filter(found -> found.getBuyerId().equals(principal.userId()))
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
      events.publishEvent(new RequestEvents.StatsChanged(requestId));
      return mapper.detail(request, recipients.countByRequestIdAndSeenAtIsNotNull(requestId), lang);
   }

   private void recalculateRating(Long shopId) {
      Shop shop = shops.findById(shopId).orElseThrow();
      Double average = reviews.averageStars(shopId);
      BigDecimal rating = average == null ? BigDecimal.ZERO
            : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
      shop.updateRating(rating, (int) reviews.countByShopId(shopId));
   }

   // ─────────────────────── таймер ───────────────────────

   /**
    * Время вышло — EXPIRED: у продавцов без ответа запрос уходит в «Истёкшие», покупателю — пуш
    * «Время вышло: 3 ответа. Продлить?» или «Пока никто не ответил». Возвращает число запросов.
    */
   @Transactional
   public int expireDue() {
      Instant now = clock.instant();
      List<PartRequest> due = requests.findExpiring(RequestStatus.ACTIVE, now, PageRequest.of(0, JOB_BATCH));
      if (due.isEmpty()) {
         return 0;
      }
      due.forEach(request -> request.expire(now));
      recipients.expire(due.stream().map(PartRequest::getId).toList());
      for (PartRequest request : due) {
         events.publishEvent(new RequestEvents.Expired(request.getId(), request.getHaveCount()));
         events.publishEvent(new RequestEvents.StatsChanged(request.getId()));
      }
      return due.size();
   }

   /** Закрытый покупателем запрос: ни ответить, ни продлить. */
   static void requireOpen(PartRequest request) {
      if (!request.isOpen()) {
         throw new ConflictException("REQUEST_CLOSED", "Запрос уже закрыт");
      }
   }

   /** Ответить можно только до expiresAt (спецификация 3.7а). */
   static void requireActive(PartRequest request) {
      requireOpen(request);
      if (!request.isActive()) {
         throw new ConflictException("REQUEST_EXPIRED", "Время запроса вышло — ответить уже нельзя");
      }
   }

   static NotFoundException notFound() {
      return new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден");
   }
}
