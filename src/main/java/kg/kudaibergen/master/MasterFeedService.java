package kg.kudaibergen.master;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.chat.ChatRepository;
import kg.kudaibergen.chat.ChatService;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.master.dto.MasterFeedItemDto;
import kg.kudaibergen.master.dto.ServiceInputs;
import kg.kudaibergen.master.dto.ServiceOfferDto;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceOffer;
import kg.kudaibergen.master.entity.ServiceRecipient;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Заявки мастера (39): лента «Новые / Откликнулся / Истёкшие», «посмотрел», отклик «Могу помочь» (цена от,
 * когда могу, сообщение — клиенту пуш и чат) или «Не моё». Отклик один; после срока — 409 REQUEST_EXPIRED.
 */
@Service
public class MasterFeedService {

   private static final Instant FEED_START = Instant.parse("9999-12-31T00:00:00Z");

   private final ServiceRequestRepository requests;
   private final ServiceRecipientRepository recipients;
   private final ServiceOfferRepository offers;
   private final MasterService masters;
   private final ServiceRequestMapper mapper;
   private final ChatService chatService;
   private final ChatRepository chats;
   private final UserRepository users;
   private final ApplicationEventPublisher events;
   private final Clock clock;

   public MasterFeedService(ServiceRequestRepository requests, ServiceRecipientRepository recipients,
                            ServiceOfferRepository offers, MasterService masters, ServiceRequestMapper mapper,
                            ChatService chatService, ChatRepository chats, UserRepository users,
                            ApplicationEventPublisher events, Clock clock) {
      this.requests = requests;
      this.recipients = recipients;
      this.offers = offers;
      this.masters = masters;
      this.mapper = mapper;
      this.chatService = chatService;
      this.chats = chats;
      this.users = users;
      this.events = events;
      this.clock = clock;
   }

   /** NEW — активные без ответа, ANSWERED — «Могу помочь», EXPIRED — время вышло без ответа. */
   public enum MasterFeedFilter {
      NEW,
      ANSWERED,
      EXPIRED
   }

   @Transactional(readOnly = true)
   public CursorPage<MasterFeedItemDto> feed(Long userId, MasterFeedFilter filter, String cursor, Integer limit,
                                             Lang lang) {
      Master master = masters.requireMine(userId);
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
      List<ServiceRecipient> rows = switch (filter) {
         case NEW -> recipients.findNew(master.getId(), at, beforeId, size + 1);
         case ANSWERED -> recipients.findAnswered(master.getId(), at, beforeId, size + 1);
         case EXPIRED -> recipients.findExpired(master.getId(), at, beforeId, size + 1);
      };
      List<Long> ids = rows.stream().map(ServiceRecipient::getRequestId).toList();
      Map<Long, ServiceRequest> byId = requests.findAllById(ids).stream()
            .collect(Collectors.toMap(ServiceRequest::getId, Function.identity()));
      Map<Long, ServiceOffer> myOffers = offers.findByMasterIdAndRequestIdIn(master.getId(), ids).stream()
            .collect(Collectors.toMap(ServiceOffer::getRequestId, Function.identity()));
      Map<Long, Long> chatOfRequest = chats.findByMasterIdAndServiceRequestIdIn(master.getId(), ids).stream()
            .collect(Collectors.toMap(Chat::getServiceRequestId, Chat::getId, (a, b) -> a));
      Map<Long, String> names = buyerNames(byId.values());
      return CursorPage.of(rows, size, row -> row.getNotifiedAt() + "|" + row.getRequestId(), row -> {
         ServiceRequest request = byId.get(row.getRequestId());
         ServiceOffer offer = myOffers.get(row.getRequestId());
         return mapper.feedItem(request, row, names.get(request.getBuyerId()), offer == null ? null
               : mapper.offer(offer, null, row.getDistanceM(), chatOfRequest.get(row.getRequestId())), lang);
      });
   }

   @Transactional(readOnly = true)
   public MasterFeedItemDto one(Long userId, Long requestId, Lang lang) {
      Master master = masters.requireMine(userId);
      ServiceRecipient recipient = recipient(requestId, master.getId());
      ServiceRequest request = requests.findById(requestId).filter(found -> !found.isHiddenByAdmin())
            .orElseThrow(ServiceRequestService::notFound);
      ServiceOfferDto myOffer = offers.findByRequestIdAndMasterId(requestId, master.getId())
            .map(offer -> mapper.offer(offer, null, recipient.getDistanceM(), chatId(request, master.getId())))
            .orElse(null);
      return mapper.feedItem(request, recipient, buyerNames(List.of(request)).get(request.getBuyerId()), myOffer, lang);
   }

   /** Мастер открыл заявку или нажал на пуш — у клиента растёт «Посмотрели». */
   @Transactional
   public void seen(Long userId, Long requestId) {
      Master master = masters.requireMine(userId);
      if (recipient(requestId, master.getId()).seen(clock.instant())) {
         events.publishEvent(new ServiceEvents.ServiceStatsChanged(requestId));
      }
   }

   /** «Могу помочь» или «Не моё» (39). Один отклик на заявку. */
   @Transactional
   public ServiceOfferDto offer(Long userId, Long requestId, ServiceInputs.Offer input) {
      Master master = masters.requireMine(userId);
      if (!master.isActive()) {
         throw new ForbiddenException("MASTER_NOT_ACTIVE", "Профиль мастера не проверен или заблокирован");
      }
      ServiceRequest request = requests.findForUpdate(requestId).filter(found -> !found.isHiddenByAdmin())
            .orElseThrow(ServiceRequestService::notFound);
      ServiceRecipient recipient = recipient(requestId, master.getId());
      ServiceRequestService.requireActive(request);
      if (offers.findByRequestIdAndMasterId(requestId, master.getId()).isPresent()) {
         throw new ConflictException("ALREADY_ANSWERED", "Вы уже ответили на эту заявку");
      }
      Instant now = clock.instant();
      if (input.availableAt() != null && input.availableAt().isBefore(now.minusSeconds(60))) {
         throw new BadRequestException("BAD_TIME", "Время — не в прошлом");
      }
      ServiceOffer offer = new ServiceOffer(requestId, master.getId(), input.answer(), input.priceFrom(),
            input.availableAt(), blankToNull(input.message()), now);
      try {
         offers.saveAndFlush(offer);
      } catch (DataIntegrityViolationException parallel) {
         throw new ConflictException("ALREADY_ANSWERED", "Вы уже ответили на эту заявку");
      }
      recipient.answered(input.answer(), now);
      Long chatId = null;
      if (offer.canHelp()) {
         request.canHelpChanged(1);
         chatId = chatService.openForServiceOffer(request, offer, master, userId);
         events.publishEvent(new ServiceEvents.ServiceOfferReceived(requestId, offer.getId()));
      }
      events.publishEvent(new ServiceEvents.ServiceStatsChanged(requestId));
      return mapper.offer(offer, null, recipient.getDistanceM(), chatId);
   }

   private Long chatId(ServiceRequest request, Long masterId) {
      return chats.findByMasterIdAndServiceRequestIdIn(masterId, List.of(request.getId())).stream()
            .map(Chat::getId).findFirst().orElse(null);
   }

   private ServiceRecipient recipient(Long requestId, Long masterId) {
      return recipients.findByRequestIdAndMasterId(requestId, masterId).orElseThrow(ServiceRequestService::notFound);
   }

   private Map<Long, String> buyerNames(Collection<ServiceRequest> list) {
      return users.findAllById(list.stream().map(ServiceRequest::getBuyerId).distinct().toList()).stream()
            .filter(user -> user.getName() != null)
            .collect(Collectors.toMap(User::getId, User::getName));
   }

   private static String blankToNull(String text) {
      return text == null || text.isBlank() ? null : text.trim();
   }
}
