package kg.kudaibergen.request;

import java.util.List;
import java.util.Map;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.ChatChannels;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Живая статистика запроса (32): после коммита покупателю в личный канал inbox:{userId}#{userId}
 * уходит событие REQUEST_STATS с полной статистикой. Источник истины — GET /requests/{id}/stats.
 * Новый запрос (11): людям бокса в их личные каналы — NEW_REQUEST {requestId, shopId}, чтобы список
 * «Новые» обновился сразу, пока приложение открыто (пуш на переднем плане список не обновляет).
 */
@Component
public class RequestRealtime {

   static final String REQUEST_STATS = "REQUEST_STATS";
   static final String NEW_REQUEST = "NEW_REQUEST";

   private final PartRequestRepository requests;
   private final RequestStatsView stats;
   private final CentrifugoClient centrifugo;
   private final ShopMemberRepository members;

   public RequestRealtime(PartRequestRepository requests, RequestStatsView stats, CentrifugoClient centrifugo,
                          ShopMemberRepository members) {
      this.requests = requests;
      this.stats = stats;
      this.centrifugo = centrifugo;
      this.members = members;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(RequestEvents.Dispatched event) {
      if (event.shopIds().isEmpty()) {
         return;
      }
      List<ShopMember> staff = members.findByShopIdIn(event.shopIds());
      centrifugo.publishAll(staff.stream()
            .map(member -> new CentrifugoClient.Publication(ChatChannels.inbox(member.getUserId()),
                  new ChatEvent(NEW_REQUEST, Map.of("requestId", event.requestId(), "shopId", member.getShopId()))))
            .toList());
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(RequestEvents.StatsChanged event) {
      requests.findById(event.requestId()).ifPresent(request -> centrifugo.publish(
            ChatChannels.inbox(request.getBuyerId()), new ChatEvent(REQUEST_STATS, stats.build(request))));
   }
}
