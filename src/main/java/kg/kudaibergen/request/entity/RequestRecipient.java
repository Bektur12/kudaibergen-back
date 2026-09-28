package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Запрос доставлен боксу: лента продавца (11) и статистика «без ответа», «время ответа» (17). */
@Entity
@Table(name = "request_recipients")
@IdClass(RequestRecipientId.class)
public class RequestRecipient {

   @Id
   @Column(name = "request_id")
   private Long requestId;

   @Id
   @Column(name = "shop_id")
   private Long shopId;

   @Column(name = "notified_at", nullable = false, updatable = false)
   private Instant notifiedAt;

   @Column(name = "seen_at")
   private Instant seenAt;

   @Column(name = "replied_at")
   private Instant repliedAt;

   protected RequestRecipient() {
   }

   public RequestRecipient(Long requestId, Long shopId, Instant notifiedAt) {
      this.requestId = requestId;
      this.shopId = shopId;
      this.notifiedAt = notifiedAt;
   }

   public void seen(Instant at) {
      if (seenAt == null) {
         seenAt = at;
      }
   }

   public void replied(Instant at) {
      seen(at);
      repliedAt = at;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Instant getNotifiedAt() {
      return notifiedAt;
   }

   public Instant getSeenAt() {
      return seenAt;
   }

   public Instant getRepliedAt() {
      return repliedAt;
   }
}
