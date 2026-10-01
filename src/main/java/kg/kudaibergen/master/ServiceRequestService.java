package kg.kudaibergen.master;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import kg.kudaibergen.chat.ChatRepository;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.CarOrigins;
import kg.kudaibergen.garage.GarageService;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.ServiceEstimateDto;
import kg.kudaibergen.master.dto.ServiceInputs;
import kg.kudaibergen.master.dto.ServiceOfferDto;
import kg.kudaibergen.master.dto.ServiceRequestDetailDto;
import kg.kudaibergen.master.dto.ServiceRequestSummaryDto;
import kg.kudaibergen.master.dto.ServiceStatsDto;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.MasterReview;
import kg.kudaibergen.master.entity.OfferAnswer;
import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.master.entity.ServiceOffer;
import kg.kudaibergen.master.entity.ServiceRecipient;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.ReviewTag;
import kg.kudaibergen.user.entity.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Заявка на услугу со стороны клиента (12.4–12.6): кому уйдёт, отправка, «Мои заявки», отклики «Могу помочь»,
 * статистика, продление, расширение радиуса, «Договорились» с оценкой. Таймер переводит просроченные в EXPIRED.
 */
@Service
public class ServiceRequestService {

   private static final Logger log = LoggerFactory.getLogger(ServiceRequestService.class);
   private static final Duration DAY = Duration.ofDays(1);
   private static final int JOB_BATCH = 200;
   static final Set<Integer> EXTEND_MINUTES = Set.of(30, 60, 180);
   static final int DEFAULT_RADIUS_KM = 5;

   private final ServiceRequestRepository requests;
   private final ServiceRecipientRepository recipients;
   private final ServiceOfferRepository offers;
   private final MasterRepository masters;
   private final MasterReviewRepository reviews;
   private final ChatRepository chats;
   private final ServiceRecipientFinder finder;
   private final ServiceRequestMapper mapper;
   private final MasterMapper masterMapper;
   private final ServiceCatalog catalog;
   private final GarageService garage;
   private final VehicleDirectory directory;
   private final MediaService media;
   private final ApplicationEventPublisher events;
   private final AppProperties.Requests limits;
   private final Clock clock;

   public ServiceRequestService(ServiceRequestRepository requests, ServiceRecipientRepository recipients,
                                ServiceOfferRepository offers, MasterRepository masters, MasterReviewRepository reviews,
                                ChatRepository chats, ServiceRecipientFinder finder, ServiceRequestMapper mapper,
                                MasterMapper masterMapper, ServiceCatalog catalog, GarageService garage,
                                VehicleDirectory directory, MediaService media, ApplicationEventPublisher events,
                                AppProperties properties, Clock clock) {
      this.requests = requests;
      this.recipients = recipients;
      this.offers = offers;
      this.masters = masters;
      this.reviews = reviews;
      this.chats = chats;
      this.finder = finder;
      this.mapper = mapper;
      this.masterMapper = masterMapper;
      this.catalog = catalog;
      this.garage = garage;
      this.directory = directory;
      this.media = media;
      this.events = events;
      this.limits = properties.requests();
      this.clock = clock;
   }

   // ─────────────────────── отправка ───────────────────────

   /** «Заявку получат 12 мастеров — Шиномонтаж для Toyota» (36): пересчитывается при каждом изменении. */
   @Transactional(readOnly = true)
   public ServiceEstimateDto estimate(Long buyerId, String service, Long carId, Long brandId, CarOrigin origin,
                                      double lat, double lng, Integer radiusKm, Lang lang) {
      ServiceType type = catalog.require(service);
      ServiceRequest.CarSnapshot car = car(buyerId, carId, brandId, null, null, null, null, origin);
      Brand brand = directory.brand(car.brandId());
      int count = finder.find(buyerId, service, car.brandId(), car.origin(), lat, lng, radius(radiusKm)).size();
      return new ServiceEstimateDto(count, type.name(lang) + " · " + brand.getName());
   }

   /** «Отправить» (36): заявка уходит подходящим мастерам рядом, им — пуш. Некому — 409 NO_RECIPIENTS. */
   @Transactional
   public ServiceRequestDetailDto create(Long buyerId, ServiceInputs.CreateServiceRequest input, Lang lang) {
      ServiceType type = catalog.require(input.service());
      ServiceRequest.CarSnapshot car = car(buyerId, input.carId(), input.brandId(), input.modelId(), input.year(),
            input.engineVolume(), input.fuel(), input.origin());
      if ((input.when() == ServiceWhen.AT_TIME) != (input.atTime() != null)) {
         throw new BadRequestException("AT_TIME_REQUIRED", "Укажите время или выберите «Сейчас / Сегодня / Завтра»");
      }
      List<Long> photoIds = input.mediaIds() == null ? List.of() : input.mediaIds().stream().distinct().toList();
      if (!photoIds.isEmpty()) {
         media.requireUsable(photoIds, List.of(buyerId), Set.of(MediaPurpose.SERVICE, MediaPurpose.REQUEST), true);
      }
      Instant now = clock.instant();
      checkLimits(buyerId, now);
      int radius = radius(input.radiusKm());
      List<ServiceRecipientFinder.Match> found = finder.find(buyerId, type.getCode(), car.brandId(), car.origin(),
            input.lat(), input.lng(), radius);
      if (found.isEmpty()) {
         throw new ConflictException("NO_RECIPIENTS",
               "Рядом сейчас нет мастеров, которые берутся за это — увеличьте радиус или попробуйте позже");
      }
      ServiceDuration duration = input.duration() != null ? input.duration()
            : ServiceDuration.ofMinutes(type.getDurationMin());
      ServiceRequest request = requests.save(new ServiceRequest(buyerId, type.getCode(), car,
            input.description().trim(), input.when(), input.atTime(), input.where(), input.lat(), input.lng(),
            blankToNull(input.address()), radius, photoIds, duration, now));
      dispatch(request, found, Set.of(), now);
      return mapper.detail(request, 0, lang);
   }

   /** «Расширить радиус» (+5 км, до 50): новым мастерам — пуш, срок заново, истёкшая снова активна. */
   @Transactional
   public ServiceRequestDetailDto widen(AuthPrincipal principal, Long requestId, Lang lang) {
      ServiceRequest request = ownForUpdate(principal, requestId);
      requireOpen(request);
      if (!request.canWiden()) {
         throw new ConflictException("RADIUS_LIMIT", "Радиус уже максимальный — " + ServiceRequest.MAX_RADIUS_KM + " км");
      }
      Instant now = clock.instant();
      reactivate(request);
      Set<Long> already = recipients.findMasterIds(requestId);
      request.widen(now);
      dispatch(request, finder.find(request.getBuyerId(), request.getService(), request.getBrandId(),
            request.getOrigin(), request.getLat(), request.getLng(), request.getRadiusKm()), already, now);
      return mapper.detail(request, seen(requestId), lang);
   }

   /** «Продлить» на 30, 60 или 180 минут — до 3 раз. */
   @Transactional
   public ServiceRequestDetailDto extend(AuthPrincipal principal, Long requestId, ServiceInputs.ServiceExtend input, Lang lang) {
      if (!EXTEND_MINUTES.contains(input.minutes())) {
         throw new BadRequestException("BAD_EXTEND", "Продлить можно на 30, 60 или 180 минут");
      }
      ServiceRequest request = ownForUpdate(principal, requestId);
      requireOpen(request);
      if (!request.canExtend()) {
         throw new ConflictException("EXTEND_LIMIT",
               "Продлить можно не больше " + ServiceRequest.MAX_EXTENSIONS + " раз — отправьте заявку заново");
      }
      reactivate(request);
      request.extend(Duration.ofMinutes(input.minutes()), clock.instant());
      events.publishEvent(new ServiceEvents.ServiceStatsChanged(requestId));
      return mapper.detail(request, seen(requestId), lang);
   }

   private void reactivate(ServiceRequest request) {
      if (request.isActive()) {
         return;
      }
      checkActiveLimit(request.getBuyerId());
      recipients.revive(request.getId());
   }

   private void dispatch(ServiceRequest request, List<ServiceRecipientFinder.Match> found, Set<Long> already,
                         Instant now) {
      List<ServiceRecipient> added = found.stream()
            .filter(match -> !already.contains(match.master().getId()))
            .map(match -> new ServiceRecipient(request.getId(), match.master().getId(), match.distanceM(), now))
            .toList();
      recipients.saveAll(added);
      List<Long> masterIds = added.stream().map(ServiceRecipient::getMasterId).toList();
      request.dispatched(masterIds.size());
      events.publishEvent(new ServiceEvents.ServiceDispatched(request.getId(), masterIds));
      events.publishEvent(new ServiceEvents.ServiceStatsChanged(request.getId()));
      log.info("Заявка {} разослана {} мастерам", request.getId(), masterIds.size());
   }

   /** Машина: из гаража или марка (+ модель, год, объём, топливо); страна — выбранная, из гаража или по марке. */
   private ServiceRequest.CarSnapshot car(Long buyerId, Long carId, Long brandId, Long modelId, Integer year,
                                          BigDecimal engineVolume, kg.kudaibergen.garage.entity.FuelType fuel,
                                          CarOrigin origin) {
      if (carId != null) {
         Car car = garage.getOwned(carId, buyerId);
         CarOrigin carOrigin = origin != null ? origin
               : car.getOrigin() != null ? car.getOrigin() : CarOrigins.of(car.getBrand().getSlug());
         return new ServiceRequest.CarSnapshot(car.getId(), car.getBrand().getId(), car.getModel().getId(),
               car.getYear(), engineVolume != null ? engineVolume : car.getEngineVolume(),
               fuel != null ? fuel : car.getFuel(), carOrigin);
      }
      if (brandId == null) {
         throw new BadRequestException("CAR_REQUIRED", "Выберите машину из гаража или марку");
      }
      Brand brand = directory.brand(brandId);
      if (modelId != null) {
         CarModel model = directory.model(modelId);
         if (!model.getBrandId().equals(brandId)) {
            throw new BadRequestException("MODEL_MISMATCH", "Модель не этой марки");
         }
      }
      return new ServiceRequest.CarSnapshot(null, brandId, modelId, year == null ? null : year.shortValue(),
            engineVolume, fuel, origin != null ? origin : CarOrigins.of(brand.getSlug()));
   }

   private static int radius(Integer radiusKm) {
      return radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
   }

   /** Не больше активных заявок и новых за сутки, чем запросов на запчасти (app.requests). */
   private void checkLimits(Long buyerId, Instant now) {
      checkActiveLimit(buyerId);
      if (requests.countByBuyerIdAndCreatedAtAfter(buyerId, now.minus(DAY)) >= limits.maxPerDay()) {
         throw new RateLimitException("DAILY_REQUESTS_LIMIT", "Не больше " + limits.maxPerDay() + " заявок в сутки",
               DAY.toSeconds());
      }
   }

   private void checkActiveLimit(Long buyerId) {
      if (requests.countByBuyerIdAndStatus(buyerId, RequestStatus.ACTIVE) >= limits.maxOpen()) {
         throw new ConflictException("OPEN_REQUESTS_LIMIT",
               "У вас уже " + limits.maxOpen() + " активных заявок — закройте ненужные");
      }
   }

   // ─────────────────────── просмотр ───────────────────────

   @Transactional(readOnly = true)
   public CursorPage<ServiceRequestSummaryDto> mine(Long buyerId, String cursor, Integer limit, Lang lang) {
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
      List<ServiceRequest> rows = requests.findMine(buyerId, RequestStatus.ACTIVE, rank, beforeId,
            PageRequest.of(0, size + 1));
      return CursorPage.of(rows, size, request -> (request.isActive() ? 0 : 1) + ":" + request.getId(),
            request -> mapper.summary(request, lang));
   }

   @Transactional(readOnly = true)
   public ServiceRequestDetailDto detail(AuthPrincipal principal, Long requestId, Lang lang) {
      return mapper.detail(visible(principal, requestId), seen(requestId), lang);
   }

   /** Отклики «Могу помочь» (37) по времени; «Не моё» клиент видит только числом в статистике. */
   @Transactional(readOnly = true)
   public List<ServiceOfferDto> offers(AuthPrincipal principal, Long requestId, Long afterId) {
      visible(principal, requestId);
      List<ServiceOffer> list = offers.findCanHelp(requestId, OfferAnswer.CAN_HELP, afterId == null ? 0 : afterId);
      List<Long> masterIds = list.stream().map(ServiceOffer::getMasterId).toList();
      var cards = masterMapper.cards(masters.findAllById(masterIds));
      Map<Long, Integer> distance = recipients.findByRequestIdAndMasterIdIn(requestId, masterIds).stream()
            .collect(Collectors.toMap(ServiceRecipient::getMasterId, ServiceRecipient::getDistanceM));
      Map<Long, Long> chatOfMaster = chats.findByServiceRequestId(requestId).stream()
            .filter(chat -> chat.getMasterId() != null)
            .collect(Collectors.toMap(Chat::getMasterId, Chat::getId, (a, b) -> a));
      return list.stream().map(offer -> mapper.offer(offer, cards.get(offer.getMasterId()),
            distance.getOrDefault(offer.getMasterId(), 0), chatOfMaster.get(offer.getMasterId()))).toList();
   }

   @Transactional(readOnly = true)
   public ServiceStatsDto stats(AuthPrincipal principal, Long requestId) {
      return stats(visible(principal, requestId));
   }

   /** Для REST и живого обновления. */
   public ServiceStatsDto stats(ServiceRequest request) {
      ServiceRecipientRepository.ServiceCounts counts = recipients.counts(request.getId());
      long silent = Math.max(0, counts.getDelivered() - counts.getCanHelp() - counts.getNotMine());
      Instant now = clock.instant();
      long durationMin = Duration.between(request.getSentAt(), request.getExpiresAt()).toMinutes();
      long remainingSec = request.isActive() ? Math.max(0, Duration.between(now, request.getExpiresAt()).toSeconds()) : 0;
      return new ServiceStatsDto(request.getId(), request.getStatus(), request.getExpiresAt(), durationMin,
            (remainingSec + 59) / 60, request.canExtend(), request.getExtendedTimes(), request.getRadiusKm(),
            new ServiceStatsDto.ServiceRecipientCounts(counts.getDelivered(), counts.getSeen(), counts.getCanHelp(),
                  counts.getNotMine(), silent));
   }

   private ServiceRequest visible(AuthPrincipal principal, Long requestId) {
      return requests.findById(requestId)
            .filter(request -> request.getBuyerId().equals(principal.userId()))
            .orElseThrow(ServiceRequestService::notFound);
   }

   private ServiceRequest ownForUpdate(AuthPrincipal principal, Long requestId) {
      return requests.findForUpdate(requestId)
            .filter(found -> found.getBuyerId().equals(principal.userId()))
            .orElseThrow(ServiceRequestService::notFound);
   }

   private long seen(Long requestId) {
      return recipients.counts(requestId).getSeen();
   }

   // ─────────────────────── закрытие ───────────────────────

   /**
    * «Договорились» (37): с мастером — только с откликнувшимся «Могу помочь», оценка ему сразу пересчитывает
    * рейтинг. Без мастера — просто закрыть. Можно и после истечения времени.
    */
   @Transactional
   public ServiceRequestDetailDto close(AuthPrincipal principal, Long requestId, ServiceInputs.Close input, Lang lang) {
      ServiceRequest request = ownForUpdate(principal, requestId);
      requireOpen(request);
      Long masterId = input.masterId();
      if (input.stars() != null && masterId == null) {
         throw new BadRequestException("MASTER_REQUIRED", "Оценку можно поставить только мастеру, с которым договорились");
      }
      if (masterId != null && !offers.existsByRequestIdAndMasterIdAndAnswer(requestId, masterId, OfferAnswer.CAN_HELP)) {
         throw new BadRequestException("MASTER_NOT_OFFERED", "Этот мастер не откликался на заявку");
      }
      Instant now = clock.instant();
      request.close(masterId, now);
      if (input.stars() != null) {
         List<ReviewTag> tags = input.tags() == null ? List.of() : List.copyOf(new HashSet<>(input.tags()));
         reviews.saveAndFlush(new MasterReview(masterId, principal.userId(), requestId, input.stars(), tags, now));
         Master master = masters.findById(masterId).orElseThrow();
         Double average = reviews.averageStars(masterId);
         master.updateRating(average == null ? BigDecimal.ZERO : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP),
               (int) reviews.countByMasterId(masterId));
      }
      events.publishEvent(new ServiceEvents.ServiceClosed(requestId, masterId, input.stars()));
      events.publishEvent(new ServiceEvents.ServiceStatsChanged(requestId));
      return mapper.detail(request, seen(requestId), lang);
   }

   // ─────────────────────── таймер ───────────────────────

   /** Время вышло — EXPIRED, мастерам без ответа — «Истёкшие», клиенту — пуш. Возвращает число заявок. */
   @Transactional
   public int expireDue() {
      Instant now = clock.instant();
      List<ServiceRequest> due = requests.findExpiring(RequestStatus.ACTIVE, now, PageRequest.of(0, JOB_BATCH));
      if (due.isEmpty()) {
         return 0;
      }
      due.forEach(request -> request.expire(now));
      recipients.expire(due.stream().map(ServiceRequest::getId).toList());
      for (ServiceRequest request : due) {
         events.publishEvent(new ServiceEvents.ServiceExpired(request.getId(), request.getCanHelpCount()));
         events.publishEvent(new ServiceEvents.ServiceStatsChanged(request.getId()));
      }
      return due.size();
   }

   static void requireOpen(ServiceRequest request) {
      if (!request.isOpen()) {
         throw new ConflictException("REQUEST_CLOSED", "Заявка уже закрыта");
      }
   }

   static void requireActive(ServiceRequest request) {
      requireOpen(request);
      if (!request.isActive()) {
         throw new ConflictException("REQUEST_EXPIRED", "Время заявки вышло — откликнуться уже нельзя");
      }
   }

   static NotFoundException notFound() {
      return new NotFoundException("SERVICE_REQUEST_NOT_FOUND", "Заявка не найдена");
   }

   private static String blankToNull(String text) {
      return text == null || text.isBlank() ? null : text.trim();
   }
}
