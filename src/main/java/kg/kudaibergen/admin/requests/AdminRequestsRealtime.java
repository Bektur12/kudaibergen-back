package kg.kudaibergen.admin.requests;

import java.util.Map;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.master.ServiceEvents;
import kg.kudaibergen.request.RequestEvents;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Живой мониторинг [A8]: на канал admin:requests уходит {type: REQUEST_CHANGED, payload: {kind, id, event}} —
 * новый запрос или заявка, ответ, отклик, истёк, закрыт. Админка перечитывает строку или счётчики.
 */
@Component
public class AdminRequestsRealtime {

   public static final String CHANNEL = "admin:requests";

   private final CentrifugoClient centrifugo;

   public AdminRequestsRealtime(CentrifugoClient centrifugo) {
      this.centrifugo = centrifugo;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Dispatched event) {
      publish("PART", event.requestId(), "DISPATCHED");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.HaveReceived event) {
      publish("PART", event.requestId(), "REPLY");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Expired event) {
      publish("PART", event.requestId(), "EXPIRED");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Closed event) {
      publish("PART", event.requestId(), "CLOSED");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceDispatched event) {
      publish("SERVICE", event.requestId(), "DISPATCHED");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceOfferReceived event) {
      publish("SERVICE", event.requestId(), "REPLY");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceExpired event) {
      publish("SERVICE", event.requestId(), "EXPIRED");
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceClosed event) {
      publish("SERVICE", event.requestId(), "CLOSED");
   }

   /** Изменение из самой админки (скрыть): событие уходит после коммита. */
   public void afterCommit(String kind, Long requestId, String event) {
      if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
         org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
               new org.springframework.transaction.support.TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                     publish(kind, requestId, event);
                  }
               });
      } else {
         publish(kind, requestId, event);
      }
   }

   private void publish(String kind, Long requestId, String event) {
      centrifugo.publish(CHANNEL, new ChatEvent("REQUEST_CHANGED",
            Map.of("kind", kind, "id", requestId, "event", event)));
   }
}
