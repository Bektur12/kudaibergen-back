package kg.kudaibergen.master;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.ChatChannels;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Живая статистика заявки (37): после коммита клиенту в inbox:{userId}#{userId} — событие SERVICE_REQUEST_STATS. */
@Component
public class ServiceRealtime {

   static final String SERVICE_REQUEST_STATS = "SERVICE_REQUEST_STATS";

   private final ServiceRequestRepository requests;
   private final ServiceRequestService service;
   private final CentrifugoClient centrifugo;

   public ServiceRealtime(ServiceRequestRepository requests, ServiceRequestService service, CentrifugoClient centrifugo) {
      this.requests = requests;
      this.service = service;
      this.centrifugo = centrifugo;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ServiceEvents.ServiceStatsChanged event) {
      requests.findById(event.requestId()).ifPresent(request -> centrifugo.publish(
            ChatChannels.inbox(request.getBuyerId()), new ChatEvent(SERVICE_REQUEST_STATS, service.stats(request))));
   }
}
