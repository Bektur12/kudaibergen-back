package kg.kudaibergen.master.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Отклик мастера (37, 39): «Могу помочь» с ценой «от», временем и сообщением, или «Не моё». Один на заявку. */
@Entity
@Table(name = "service_offers")
public class ServiceOffer {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "request_id", nullable = false, updatable = false)
   private Long requestId;

   @Column(name = "master_id", nullable = false, updatable = false)
   private Long masterId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, updatable = false, length = 8)
   private OfferAnswer answer;

   @Column(name = "price_from", updatable = false)
   private Integer priceFrom;

   @Column(name = "available_at", updatable = false)
   private Instant availableAt;

   @Column(updatable = false, length = 300)
   private String message;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt;

   protected ServiceOffer() {
   }

   public ServiceOffer(Long requestId, Long masterId, OfferAnswer answer, Integer priceFrom, Instant availableAt,
                       String message, Instant now) {
      this.requestId = requestId;
      this.masterId = masterId;
      this.answer = answer;
      boolean canHelp = answer == OfferAnswer.CAN_HELP;
      this.priceFrom = canHelp ? priceFrom : null;
      this.availableAt = canHelp ? availableAt : null;
      this.message = canHelp ? message : null;
      this.createdAt = now;
      this.updatedAt = now;
   }

   public boolean canHelp() {
      return answer == OfferAnswer.CAN_HELP;
   }

   public Long getId() {
      return id;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getMasterId() {
      return masterId;
   }

   public OfferAnswer getAnswer() {
      return answer;
   }

   public Integer getPriceFrom() {
      return priceFrom;
   }

   public Instant getAvailableAt() {
      return availableAt;
   }

   public String getMessage() {
      return message;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
