package kg.kudaibergen.master;

import java.util.Map;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.ChatChannels;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Живая статистика заявки (37): после коммита клиенту в inbox:{userId}#{userId} — событие SERVICE_REQUEST_STATS.
 * Новая заявка (39): владельцам мастеров — NEW_SERVICE_REQUEST {requestId, masterId}, чтобы список обновился сразу.
 */
@Component
public class ServiceRealtime {

   static final String SERVICE_REQUEST_STATS = "SERVICE_REQUEST_STATS";
   static final String NEW_SERVICE_REQUEST = "NEW_SERVICE_REQUEST";

   private final ServiceRequestRepository requests;
   private final ServiceRequestService service;
   private final CentrifugoClient centrifugo;
   private final MasterRepository masters;

   public ServiceRealtime(ServiceRequestRepository requests, ServiceRequestService service, CentrifugoClient centrifugo,
                          MasterRepository masters) {
      this.requests = requests;
      this.service = service;
      this.centrifugo = centrifugo;
      this.masters = masters;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ServiceEvents.ServiceDispatched event) {
      if (event.masterIds().isEmpty()) {
         return;
      }
      centrifugo.publishAll(masters.findAllById(event.masterIds()).stream()
            .map(master -> new CentrifugoClient.Publication(ChatChannels.inbox(master.getOwnerId()),
                  new ChatEvent(NEW_SERVICE_REQUEST, Map.of("requestId", event.requestId(), "masterId", master.getId()))))
            .toList());
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ServiceEvents.ServiceStatsChanged event) {
      requests.findById(event.requestId()).ifPresent(request -> centrifugo.publish(
            ChatChannels.inbox(request.getBuyerId()), new ChatEvent(SERVICE_REQUEST_STATS, service.stats(request))));
   }
}
