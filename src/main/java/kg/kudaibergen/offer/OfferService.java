package kg.kudaibergen.offer;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.chat.ChatService;
import kg.kudaibergen.chat.dto.SendMessageRequest;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.offer.dto.BulkReplyRequest;
import kg.kudaibergen.offer.dto.BulkReplyResult;
import kg.kudaibergen.offer.dto.CreateOfferRequest;
import kg.kudaibergen.offer.dto.OfferResponse;
import kg.kudaibergen.offer.entity.Offer;
import kg.kudaibergen.request.RequestRecipientRepository;
import kg.kudaibergen.request.RequestRepository;
import kg.kudaibergen.request.entity.Request;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

   private static final Logger log = LoggerFactory.getLogger(OfferService.class);

   private final OfferRepository offers;
   private final RequestRepository requests;
   private final RequestRecipientRepository recipients;
   private final StoreService storeService;
   private final ChatService chatService;

   public OfferService(OfferRepository offers, RequestRepository requests,
                       RequestRecipientRepository recipients, StoreService storeService,
                       ChatService chatService) {
      this.offers = offers;
      this.requests = requests;
      this.recipients = recipients;
      this.storeService = storeService;
      this.chatService = chatService;
   }

   @Transactional
   public OfferResponse create(Long sellerUserId, CreateOfferRequest command) {
      Long storeId = storeService.requireOwnStoreId(sellerUserId);
      Request request = requests.findById(command.requestId())
            .orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден"));

      RequestRecipient recipient = requireRecipient(request.getId(), storeId);
      requireAnswerable(request);
      if (offers.existsActive(request.getId(), storeId)) {
         throw new ConflictException("OFFER_ALREADY_EXISTS", "Вы уже ответили на этот запрос");
      }

      Offer offer = offers.save(Offer.of(request.getId(), storeId, command.price(),
            command.comment(), command.deliveryDays()));
      recipient.markReplied(Instant.now());
      requests.incrementOfferCount(request.getId());
      deliverAsChatMessage(request, storeId, sellerUserId, command.price(), command.comment(),
            command.deliveryDays());

      return OfferResponse.of(offer);
   }

   /**
    * Массовый ответ — главная фича продавца: один шаблон сразу на N запросов.
    * Всё в одной транзакции: либо все ответы прошли, либо ни одного.
    */
   @Transactional
   public BulkReplyResult bulkReply(Long sellerUserId, BulkReplyRequest command) {
      Long storeId = storeService.requireOwnStoreId(sellerUserId);
      String body = command.templateId() != null
            ? storeService.getOwnedTemplate(command.templateId(), storeId).getBody()
            : command.text();
      if (body == null || body.isBlank()) {
         throw new BadRequestException("REPLY_BODY_REQUIRED", "Нужен шаблон или текст ответа", "text");
      }

      List<Request> targets = requests.findActiveByIds(command.requestIds(), Instant.now());
      int created = 0;
      int skipped = 0;

      for (Request request : targets) {
         // магазин действительно получал этот запрос?
         var recipient = recipients.find(request.getId(), storeId);
         if (recipient.isEmpty()) {
            skipped++;
            continue;
         }
         // уже отвечал?
         if (offers.existsActive(request.getId(), storeId)) {
            skipped++;
            continue;
         }
         offers.save(Offer.of(request.getId(), storeId, command.price(), body, command.deliveryDays()));
         recipient.get().markReplied(Instant.now());
         requests.incrementOfferCount(request.getId());
         deliverAsChatMessage(request, storeId, sellerUserId, command.price(), body, command.deliveryDays());
         created++;
      }
      // запросы, которых нет в выборке активных, тоже считаем пропущенными
      skipped += command.requestIds().size() - targets.size();
      log.info("Массовый ответ магазина {}: создано {}, пропущено {}", storeId, created, skipped);

      return new BulkReplyResult(created, skipped);
   }

   private RequestRecipient requireRecipient(Long requestId, Long storeId) {
      return recipients.find(requestId, storeId)
            .orElseThrow(() -> new ForbiddenException("REQUEST_NOT_FOR_STORE",
                  "Запрос не приходил этому магазину"));
   }

   private void requireAnswerable(Request request) {
      if (request.getStatus() != RequestStatus.ACTIVE) {
         throw new ConflictException("REQUEST_NOT_ACTIVE", "Запрос уже закрыт");
      }
      if (!request.getExpiresAt().isAfter(Instant.now())) {
         throw new ConflictException("REQUEST_EXPIRED", "Срок ответа на запрос истёк");
      }
   }

   /**
    * Покупатель не ждёт отдельный экран с предложениями — оффер сразу прилетает сообщением
    * в чат с этим магазином (чат создаётся здесь же, если его ещё не было). Так оффер
    * бесплатно получает живую доставку, живой инбокс и умный пуш (не шлётся, если покупатель
    * и так смотрит этот чат) — всё, что уже есть у обычных сообщений.
    */
   private void deliverAsChatMessage(Request request, Long storeId, Long sellerUserId, Integer price,
                                     String text, Short deliveryDays) {
      Chat chat = chatService.getOrCreate(request.getBuyerId(), storeId, request.getId());
      String message = formatOfferMessage(price, text, deliveryDays);
      chatService.send(chat.getId(), sellerUserId, new SendMessageRequest(message));
   }

   private String formatOfferMessage(Integer price, String text, Short deliveryDays) {
      StringBuilder sb = new StringBuilder();
      if (price != null) {
         sb.append("💰 ").append(price).append(" сом");
      }
      if (deliveryDays != null) {
         if (sb.length() > 0) {
            sb.append(" · ");
         }
         sb.append("🚚 ").append(deliveryDays).append(" дн.");
      }
      if (text != null && !text.isBlank()) {
         if (sb.length() > 0) {
            sb.append("\n");
         }
         sb.append(text.trim());
      }
      return sb.length() > 0 ? sb.toString() : "Ответил на ваш запрос";
   }
}
