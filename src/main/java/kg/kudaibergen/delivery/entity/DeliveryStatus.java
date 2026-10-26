package kg.kudaibergen.delivery.entity;

/**
 * NEW — покупатель заказал; PACKED — продавец упаковал; COURIER_ORDERED — админ отправил заявку Ылдам;
 * PICKED_UP — курьер забрал с рынка (едет); DELIVERED — покупатель получил; CANCELLED — отменён админом.
 */
public enum DeliveryStatus {
   NEW,
   PACKED,
   COURIER_ORDERED,
   PICKED_UP,
   DELIVERED,
   CANCELLED;

   public boolean active() {
      return this != DELIVERED && this != CANCELLED;
   }
}
