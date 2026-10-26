package kg.kudaibergen.delivery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.admin.requests.AdminRequestsRealtime;
import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.ShopMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * После коммита: пуши по заказу с доставкой и событие живой ленты админки.
 * <pre>
 * DELIVERY_ORDER {deliveryOrderId, number, event, side: SHOP} — продавцу «Новый заказ с доставкой»;
 * DELIVERY_COURIER_ORDERED, DELIVERY_PICKED_UP {…, side: BUYER} — покупателю «Заказали курьера Ылдам»,
 * «Курьер забрал посылку»; DELIVERY_DELIVERED {…, side: SHOP} — продавцу «Покупатель получил»
 * admin:requests {type: DELIVERY_CHANGED, payload: {id, number, event, sellerName, at}}
 * </pre>
 */
@Component
public class DeliveryNotifier {

   private static final Logger log = LoggerFactory.getLogger(DeliveryNotifier.class);

   private final DeliveryOrderRepository orders;
   private final ShopMemberRepository members;
   private final DeliveryView view;
   private final UserPushes pushes;
   private final QuietHours quietHours;
   private final CentrifugoClient centrifugo;

   public DeliveryNotifier(DeliveryOrderRepository orders, ShopMemberRepository members, DeliveryView view,
                           UserPushes pushes, QuietHours quietHours, CentrifugoClient centrifugo) {
      this.orders = orders;
      this.members = members;
      this.view = view;
      this.pushes = pushes;
      this.quietHours = quietHours;
      this.centrifugo = centrifugo;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(DeliveryEvents.DeliveryChanged event) {
      DeliveryOrder order = orders.findById(event.orderId()).orElse(null);
      if (order == null) {
         return;
      }
      DeliveryView.Seller seller = view.seller(order.getShopId());
      try {
         Map<String, Object> payload = new LinkedHashMap<>();
         payload.put("id", order.getId());
         payload.put("number", order.getNumber());
         payload.put("event", event.event());
         payload.put("sellerName", seller.name());
         payload.put("at", Instant.now().toString());
         centrifugo.publish(AdminRequestsRealtime.CHANNEL, new ChatEvent("DELIVERY_CHANGED", payload));
      } catch (RuntimeException ex) {
         log.warn("Событие доставки {} в админку не ушло: {}", order.getId(), ex.getMessage());
      }
      switch (event.event()) {
         case "CREATED" -> toShop(order, "Новый заказ с доставкой",
               order.getPartTitle() + " · в " + order.getRegion().title() + " · " + order.getRecipientName(),
               event.event());
         case "COURIER_ORDERED" -> toBuyer(order, "Заказали курьера Ылдам",
               "Курьер заберёт посылку " + (view.pickupToday(order) ? "сегодня" : "завтра") + " · "
                     + order.getNumber(), event.event());
         case "PICKED_UP" -> toBuyer(order, "Курьер забрал посылку",
               (order.getRegion() == DeliveryRegion.BISHKEK ? "Едет к вам" : "Едет в " + order.getRegion().title())
                     + " · " + order.getNumber(), event.event());
         case "DELIVERED" -> toShop(order, "Покупатель получил посылку",
               order.getPartTitle() + " · " + order.getNumber(), event.event());
         default -> {
         }
      }
   }

   private void toShop(DeliveryOrder order, String title, String body, String event) {
      List<Long> staff = members.findByShopIdOrderByCreatedAtAsc(order.getShopId()).stream()
            .map(ShopMember::getUserId).toList();
      PushMessage message = new PushMessage(title, body, data(order, event, "SHOP"), null, !quietHours.now());
      pushes.send(staff, (user, settings) -> message);
   }

   private void toBuyer(DeliveryOrder order, String title, String body, String event) {
      PushMessage message = new PushMessage(title, body, data(order, event, "BUYER"), null, !quietHours.now());
      pushes.send(List.of(order.getBuyerId()), (user, settings) -> message);
   }

   private static Map<String, String> data(DeliveryOrder order, String event, String side) {
      // DELIVERY_ORDER — новый заказ, дальше DELIVERY_COURIER_ORDERED, DELIVERY_PICKED_UP, DELIVERY_DELIVERED
      String type = "CREATED".equals(event) ? "DELIVERY_ORDER" : "DELIVERY_" + event;
      return Map.of("type", type, "deliveryOrderId", order.getId().toString(),
            "number", order.getNumber(), "event", event, "side", side);
   }
}
