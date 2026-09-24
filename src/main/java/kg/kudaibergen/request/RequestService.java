package kg.kudaibergen.request;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import kg.kudaibergen.chat.ChatMediaStorage;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.notification.OutboxService;
import kg.kudaibergen.request.dto.CreateRequestRequest;
import kg.kudaibergen.request.dto.CreateRequestResponse;
import kg.kudaibergen.request.dto.RequestResponse;
import kg.kudaibergen.request.dto.SellerRequestFilter;
import kg.kudaibergen.request.dto.SellerRequestRow;
import kg.kudaibergen.request.entity.Request;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.store.StoreRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.VehicleService;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RequestService {

   private static final Logger log = LoggerFactory.getLogger(RequestService.class);

   private final RequestRepository requests;
   private final RequestRecipientRepository recipients;
   private final StoreRepository stores;
   private final UserService userService;
   private final VehicleService vehicleService;
   private final OutboxService outbox;
   private final RequestOffersView offersView;
   private final AppProperties.RequestLimits limits;
   private final ChatMediaStorage mediaStorage;

   public RequestService(RequestRepository requests, RequestRecipientRepository recipients,
                         StoreRepository stores, UserService userService, VehicleService vehicleService,
                         OutboxService outbox, RequestOffersView offersView, AppProperties properties,
                         ChatMediaStorage mediaStorage) {
      this.requests = requests;
      this.recipients = recipients;
      this.stores = stores;
      this.userService = userService;
      this.vehicleService = vehicleService;
      this.outbox = outbox;
      this.offersView = offersView;
      this.limits = properties.request();
      this.mediaStorage = mediaStorage;
   }

   /**
    * Веерная рассылка — главный алгоритм продукта.
    * Запрос сохраняется, подбираются магазины по категории и городу,
    * пуши уходят через outbox, а не в HTTP-потоке.
    */
   @Transactional
   public CreateRequestResponse create(Long buyerId, CreateRequestRequest command) {
      User buyer = userService.getRequired(buyerId);
      enforceDailyLimit(buyerId);
      validateBudget(command);

      boolean urgent = Boolean.TRUE.equals(command.isUrgent());
      Request request = new Request(buyer, command.category(), command.description().trim(), buyer.getCity());
      request.setUrgent(urgent);
      request.setBudgetMin(command.budgetMin());
      request.setBudgetMax(command.budgetMax());
      // срочный живёт 2 часа, обычный — сутки
      request.setExpiresAt(Instant.now().plus(urgent ? limits.urgentTtl() : limits.normalTtl()));

      if (command.vehicleId() != null) {
         Vehicle vehicle = vehicleService.getOwned(command.vehicleId(), buyerId);
         request.setVehicle(vehicle);
         request.setCarText(vehicle.describe());
      } else if (command.carText() != null && !command.carText().isBlank()) {
         request.setCarText(command.carText().trim());
      }
      requests.save(request);

      List<Long> storeIds = stores.findMatchingStoreIds(command.category().name());
      if (!storeIds.isEmpty()) {
         recipients.saveAll(storeIds.stream()
               .map(storeId -> new RequestRecipient(request.getId(), storeId))
               .toList());
         outbox.enqueueNewRequest(request.getId(), request.getCategory(), urgent,
               stores.findOwnerUserIds(storeIds));
      }
      log.info("Запрос {} разослан {} магазинам ({} / {})", request.getId(), storeIds.size(),
            command.category(), buyer.getCity());

      return new CreateRequestResponse(request.getId(), storeIds.size(), request.getExpiresAt());
   }

   @Transactional(readOnly = true)
   public PageResponse<RequestResponse> my(Long buyerId, int page, int size) {
      return PageResponse.of(requests.findByBuyerIdOrderByCreatedAtDesc(buyerId, PageRequest.of(page, size)),
            request -> RequestResponse.of(request, List.of(), mediaStorage.urlFor(request.getPhotoUrl())));
   }

   @Transactional(readOnly = true)
   public RequestResponse details(Long requestId, Long buyerId) {
      Request request = getOwned(requestId, buyerId);
      return RequestResponse.of(request, offersView.byRequest(requestId), mediaStorage.urlFor(request.getPhotoUrl()));
   }

   /** Продлить на сутки: и активный, и уже истёкший запрос снова становится активным. */
   @Transactional
   public RequestResponse extend(Long requestId, Long buyerId) {
      Request request = getOwned(requestId, buyerId);
      if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELLED) {
         throw new ConflictException("REQUEST_NOT_EXTENDABLE", "Запрос уже закрыт");
      }
      Instant base = request.getExpiresAt().isAfter(Instant.now()) ? request.getExpiresAt() : Instant.now();
      request.setExpiresAt(base.plus(limits.extendBy()));
      request.setStatus(RequestStatus.ACTIVE);
      return RequestResponse.of(request, offersView.byRequest(requestId), mediaStorage.urlFor(request.getPhotoUrl()));
   }

   @Transactional
   public RequestResponse cancel(Long requestId, Long buyerId) {
      Request request = getOwned(requestId, buyerId);
      if (request.getStatus() != RequestStatus.ACTIVE) {
         throw new ConflictException("REQUEST_NOT_ACTIVE", "Запрос уже не активен");
      }
      request.setStatus(RequestStatus.CANCELLED);
      return RequestResponse.of(request, offersView.byRequest(requestId), mediaStorage.urlFor(request.getPhotoUrl()));
   }

   /** Заменяет фото детали (одно на запрос) — валидация типа/размера уже внутри mediaStorage. */
   @Transactional
   public RequestResponse uploadPhoto(Long requestId, Long buyerId, MultipartFile file) {
      Request request = getOwned(requestId, buyerId);
      ChatMediaStorage.Stored stored = mediaStorage.store(file, "PHOTO");
      request.setPhotoUrl(stored.key());
      return RequestResponse.of(request, offersView.byRequest(requestId), mediaStorage.urlFor(stored.key()));
   }

   // ─────────────────────── сторона продавца ───────────────────────

   @Transactional(readOnly = true)
   public PageResponse<SellerRequestRow> forStore(Long storeId, SellerRequestFilter filter, int page, int size) {
      SellerRequestFilter effective = filter == null ? SellerRequestFilter.ALL : filter;
      return PageResponse.of(requests.findForStore(storeId,
            effective == SellerRequestFilter.URGENT,
            effective == SellerRequestFilter.UNANSWERED,
            PageRequest.of(page, size)), this::resolvePhoto);
   }

   /** JPQL-конструктор кладёт в photoUrl сырой ключ хранилища — здесь резолвим в клиентский URL. */
   private SellerRequestRow resolvePhoto(SellerRequestRow row) {
      return new SellerRequestRow(row.requestId(), row.category(), row.description(), row.car(),
            row.budgetMin(), row.budgetMax(), row.currency(), row.city(), row.isUrgent(), row.offerCount(),
            row.createdAt(), row.expiresAt(), row.seenAt(), row.repliedAt(), mediaStorage.urlFor(row.photoUrl()));
   }

   @Transactional
   public void markSeen(Long requestId, Long storeId) {
      RequestRecipient recipient = recipients.find(requestId, storeId)
            .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOR_STORE", "Запрос не приходил этому магазину"));
      recipient.markSeen();
   }

   @Transactional(readOnly = true)
   public Request getRequired(Long requestId) {
      return requests.findById(requestId)
            .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден"));
   }

   private Request getOwned(Long requestId, Long buyerId) {
      return requests.findByIdAndBuyerId(requestId, buyerId)
            .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден"));
   }

   private void enforceDailyLimit(Long buyerId) {
      int createdToday = requests.countByBuyerIdAndCreatedAtAfter(buyerId, Instant.now().minus(Duration.ofDays(1)));
      if (createdToday >= limits.dailyLimitPerBuyer()) {
         throw new RateLimitException("REQUEST_DAILY_LIMIT",
               "В сутки можно создать не больше " + limits.dailyLimitPerBuyer() + " запросов");
      }
   }

   private void validateBudget(CreateRequestRequest command) {
      if (command.budgetMin() != null && command.budgetMax() != null
            && command.budgetMin() > command.budgetMax()) {
         throw new BadRequestException("INVALID_BUDGET", "Минимальный бюджет больше максимального", "budgetMin");
      }
   }
}
