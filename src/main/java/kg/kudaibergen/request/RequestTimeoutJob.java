package kg.kudaibergen.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Раз в минуту: запросы, у которых вышло время, становятся EXPIRED, покупателю — пуш «Продлить?». */
@Component
public class RequestTimeoutJob {

   private static final Logger log = LoggerFactory.getLogger(RequestTimeoutJob.class);

   private final RequestService requests;

   public RequestTimeoutJob(RequestService requests) {
      this.requests = requests;
   }

   @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
   public void run() {
      int expired = requests.expireDue();
      if (expired > 0) {
         log.info("Запросы: истекли {}", expired);
      }
   }
}
