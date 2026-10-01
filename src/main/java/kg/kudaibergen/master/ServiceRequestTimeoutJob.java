package kg.kudaibergen.master;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Раз в минуту: заявки на услуги, у которых вышло время, становятся EXPIRED, клиенту — пуш. */
@Component
public class ServiceRequestTimeoutJob {

   private static final Logger log = LoggerFactory.getLogger(ServiceRequestTimeoutJob.class);

   private final ServiceRequestService requests;

   public ServiceRequestTimeoutJob(ServiceRequestService requests) {
      this.requests = requests;
   }

   @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
   public void run() {
      int expired = requests.expireDue();
      if (expired > 0) {
         log.info("Заявки на услуги: истекли {}", expired);
      }
   }
}
