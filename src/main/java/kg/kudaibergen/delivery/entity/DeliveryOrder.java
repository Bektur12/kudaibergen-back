package kg.kudaibergen.delivery.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.common.error.ConflictException;

/**
 * Заказ с доставкой Ылдам Экспресс: покупатель заказал с видео (NEW) → продавец упаковал (PACKED) → админ отправил
 * заявку курьеру (COURIER_ORDERED) → курьер забрал с рынка (PICKED_UP) → покупатель получил (DELIVERED).
 * Товар — снимком на момент заказа. price — тариф до 5 кг (null — назовёт курьер), доплату за вес считает курьер.
 */
@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(length = 16)
   private String number;

   @Column(name = "part_id")
   private Long partId;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "buyer_id", nullable = false, updatable = false)
   private Long buyerId;

   @Column(name = "chat_id")
   private Long chatId;

   @Column(name = "part_title", nullable = false, length = 120, updatable = false)
   private String partTitle;

   @Column(name = "part_price", nullable = false, updatable = false)
   private int partPrice;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 12, updatable = false)
   private DeliveryRegion region;

   @Enumerated(EnumType.STRING)
   @Column(length = 12, updatable = false)
   private DeliveryDistrict district;

   @Column(nullable = false, length = 200, updatable = false)
   private String address;

   @Column(name = "recipient_name", nullable = false, length = 60, updatable = false)
   private String recipientName;

   @Column(name = "recipient_phone", nullable = false, length = 20, updatable = false)
   private String recipientPhone;

   @Column(updatable = false)
   private Integer price;

   @Column(nullable = false, updatable = false)
   private short days;

   @Column(name = "weight_kg", precision = 5, scale = 1)
   private BigDecimal weightKg;

   @Column(nullable = false)
   private boolean fragile;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 16)
   private DeliveryStatus status = DeliveryStatus.NEW;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   @Column(name = "packed_at")
   private Instant packedAt;

   @Column(name = "courier_ordered_at")
   private Instant courierOrderedAt;

   @Column(name = "picked_up_at")
   private Instant pickedUpAt;

   @Column(name = "delivered_at")
   private Instant deliveredAt;

   @Column(name = "cancelled_at")
   private Instant cancelledAt;

   protected DeliveryOrder() {
   }

   public DeliveryOrder(Long partId, Long shopId, Long buyerId, String partTitle, int partPrice, DeliveryRegion region,
                        DeliveryDistrict district, String address, String recipientName, String recipientPhone,
                        Integer price, int days, Instant now) {
      this.partId = partId;
      this.shopId = shopId;
      this.buyerId = buyerId;
      this.partTitle = partTitle;
      this.partPrice = partPrice;
      this.region = region;
      this.district = district;
      this.address = address;
      this.recipientName = recipientName;
      this.recipientPhone = recipientPhone;
      this.price = price;
      this.days = (short) days;
      this.createdAt = now;
   }

   /** «KDB-000123» — от id, после первого сохранения. */
   public void assignNumber() {
      if (number == null && id != null) {
         number = String.format("KDB-%06d", id);
      }
   }

   public void packed(BigDecimal weightKg, boolean fragile, Instant now) {
      require(DeliveryStatus.NEW);
      this.weightKg = weightKg;
      this.fragile = fragile;
      this.status = DeliveryStatus.PACKED;
      this.packedAt = now;
   }

   public void courierOrdered(Instant now) {
      require(DeliveryStatus.PACKED);
      this.status = DeliveryStatus.COURIER_ORDERED;
      this.courierOrderedAt = now;
   }

   /** Курьер забрал: обычно после заявки, но продавец может нажать и раньше, если курьер пришёл сам. */
   public void pickedUp(Instant now) {
      require(DeliveryStatus.PACKED, DeliveryStatus.COURIER_ORDERED);
      this.status = DeliveryStatus.PICKED_UP;
      this.pickedUpAt = now;
   }

   /** Получил — покупатель (после того как курьер забрал или заказан) или админ. */
   public void delivered(Instant now) {
      require(DeliveryStatus.COURIER_ORDERED, DeliveryStatus.PICKED_UP);
      this.status = DeliveryStatus.DELIVERED;
      this.deliveredAt = now;
   }

   public void cancel(Instant now) {
      if (!status.active()) {
         throw badStatus();
      }
      this.status = DeliveryStatus.CANCELLED;
      this.cancelledAt = now;
   }

   private void require(DeliveryStatus... allowed) {
      for (DeliveryStatus candidate : allowed) {
         if (status == candidate) {
            return;
         }
      }
      throw badStatus();
   }

   private static ConflictException badStatus() {
      return new ConflictException("BAD_STATUS", "Заказ уже в другом статусе — обновите экран");
   }

   public void attachChat(Long chatId) {
      this.chatId = chatId;
   }

   public Long getId() {
      return id;
   }

   public String getNumber() {
      return number;
   }

   public Long getPartId() {
      return partId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Long getChatId() {
      return chatId;
   }

   public String getPartTitle() {
      return partTitle;
   }

   public int getPartPrice() {
      return partPrice;
   }

   public DeliveryRegion getRegion() {
      return region;
   }

   public DeliveryDistrict getDistrict() {
      return district;
   }

   public String getAddress() {
      return address;
   }

   public String getRecipientName() {
      return recipientName;
   }

   public String getRecipientPhone() {
      return recipientPhone;
   }

   public Integer getPrice() {
      return price;
   }

   public int getDays() {
      return days;
   }

   public BigDecimal getWeightKg() {
      return weightKg;
   }

   public boolean isFragile() {
      return fragile;
   }

   public DeliveryStatus getStatus() {
      return status;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getPackedAt() {
      return packedAt;
   }

   public Instant getCourierOrderedAt() {
      return courierOrderedAt;
   }

   public Instant getPickedUpAt() {
      return pickedUpAt;
   }

   public Instant getDeliveredAt() {
      return deliveredAt;
   }

   public Instant getCancelledAt() {
      return cancelledAt;
   }
}
