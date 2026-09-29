package kg.kudaibergen.request;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.ChatChannels;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Живая статистика запроса (32): после коммита покупателю в личный канал inbox:{userId}#{userId}
 * уходит событие REQUEST_STATS с полной статистикой. Источник истины — GET /requests/{id}/stats.
 */
@Component
public class RequestRealtime {

   static final String REQUEST_STATS = "REQUEST_STATS";

   private final PartRequestRepository requests;
   private final RequestStatsView stats;
   private final CentrifugoClient centrifugo;

   public RequestRealtime(PartRequestRepository requests, RequestStatsView stats, CentrifugoClient centrifugo) {
      this.requests = requests;
      this.stats = stats;
      this.centrifugo = centrifugo;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(RequestEvents.StatsChanged event) {
      requests.findById(event.requestId()).ifPresent(request -> centrifugo.publish(
            ChatChannels.inbox(request.getBuyerId()), new ChatEvent(REQUEST_STATS, stats.build(request))));
   }
}
