package kg.kudaibergen.offer;

import java.time.Instant;

import kg.kudaibergen.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Протухание запросов раз в минуту: сначала запросы, затем висящие на них предложения.
 * Живёт в пакете offer, потому что трогает обе таблицы (offer знает про request, не наоборот).
 */
@Component
public class ExpirationScheduler {

   private static final Logger log = LoggerFactory.getLogger(ExpirationScheduler.class);

   private final RequestRepository requests;
   private final OfferRepository offers;

   public ExpirationScheduler(RequestRepository requests, OfferRepository offers) {
      this.requests = requests;
      this.offers = offers;
   }

   @Scheduled(fixedDelay = 60_000)
   @Transactional
   public void expireOldRequests() {
      int expired = requests.expireWhereExpiresAtBefore(Instant.now());
      if (expired > 0) {
         int expiredOffers = offers.expireForExpiredRequests();
         log.info("Протухло запросов: {}, предложений: {}", expired, expiredOffers);
      }
   }
}
