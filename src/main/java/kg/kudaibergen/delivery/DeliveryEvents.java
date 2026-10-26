package kg.kudaibergen.delivery;

/** Заказ с доставкой изменился: пуши (после коммита) и живая лента админки. */
public final class DeliveryEvents {

   private DeliveryEvents() {
   }

   /** event — CREATED, PACKED, COURIER_ORDERED, PICKED_UP, DELIVERED, CANCELLED. */
   public record DeliveryChanged(Long orderId, String event) {
   }
}
