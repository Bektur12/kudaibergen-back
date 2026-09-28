package kg.kudaibergen.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Раз в минуту: «Пока никто не ответил» через 30 минут без «Есть» и EXPIRED через 7 дней без действий. */
@Component
public class RequestTimeoutJob {

   private static final Logger log = LoggerFactory.getLogger(RequestTimeoutJob.class);

   private final RequestService requests;

   public RequestTimeoutJob(RequestService requests) {
      this.requests = requests;
   }

   @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
   public void run() {
      int noReply = requests.markNoReply();
      int expired = requests.expireIdle();
      if (noReply > 0 || expired > 0) {
         log.info("Запросы: без ответа {}, истекли {}", noReply, expired);
      }
   }
}
