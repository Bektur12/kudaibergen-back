package kg.kudaibergen.offer.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "offers")
public class Offer {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "request_id", nullable = false)
   private Long requestId;

   @Column(name = "store_id", nullable = false)
   private Long storeId;

   /** NULL — это шаблонный ответ «нет в наличии». */
   @Column
   private Integer price;

   @Column(nullable = false, length = 3)
   private String currency = "KGS";

   @Column(columnDefinition = "text")
   private String comment;

   @Column(name = "delivery_days")
   private Short deliveryDays;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 40)
   private OfferStatus status = OfferStatus.ACTIVE;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Offer() {
   }

   public static Offer of(Long requestId, Long storeId, Integer price, String comment, Short deliveryDays) {
      Offer offer = new Offer();
      offer.requestId = requestId;
      offer.storeId = storeId;
      offer.price = price;
      offer.comment = comment;
      offer.deliveryDays = deliveryDays;
      return offer;
   }

   public Long getId() {
      return id;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getStoreId() {
      return storeId;
   }

   public Integer getPrice() {
      return price;
   }

   public String getCurrency() {
      return currency;
   }

   public void setCurrency(String currency) {
      this.currency = currency;
   }

   public String getComment() {
      return comment;
   }

   public Short getDeliveryDays() {
      return deliveryDays;
   }

   public OfferStatus getStatus() {
      return status;
   }

   public void setStatus(OfferStatus status) {
      this.status = status;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
