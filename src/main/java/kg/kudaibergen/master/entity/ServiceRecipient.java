package kg.kudaibergen.master.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Заявка доставлена мастеру: его лента (39) и статистика заявки для клиента (37). distanceM — до клиента. */
@Entity
@Table(name = "service_recipients")
@IdClass(ServiceRecipientId.class)
public class ServiceRecipient {

   @Id
   @Column(name = "request_id")
   private Long requestId;

   @Id
   @Column(name = "master_id")
   private Long masterId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 9)
   private ServiceRecipientStatus status = ServiceRecipientStatus.DELIVERED;

   @Column(name = "distance_m", nullable = false, updatable = false)
   private int distanceM;

   @Column(name = "notified_at", nullable = false, updatable = false)
   private Instant notifiedAt;

   @Column(name = "seen_at")
   private Instant seenAt;

   @Column(name = "replied_at")
   private Instant repliedAt;

   protected ServiceRecipient() {
   }

   public ServiceRecipient(Long requestId, Long masterId, int distanceM, Instant notifiedAt) {
      this.requestId = requestId;
      this.masterId = masterId;
      this.distanceM = distanceM;
      this.notifiedAt = notifiedAt;
   }

   /** Возвращает true, если открыл впервые. */
   public boolean seen(Instant at) {
      if (seenAt != null) {
         return false;
      }
      seenAt = at;
      if (status == ServiceRecipientStatus.DELIVERED) {
         status = ServiceRecipientStatus.SEEN;
      }
      return true;
   }

   public void answered(OfferAnswer answer, Instant at) {
      seen(at);
      if (repliedAt == null) {
         repliedAt = at;
      }
      status = answer == OfferAnswer.CAN_HELP ? ServiceRecipientStatus.CAN_HELP : ServiceRecipientStatus.NOT_MINE;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getMasterId() {
      return masterId;
   }

   public ServiceRecipientStatus getStatus() {
      return status;
   }

   public int getDistanceM() {
      return distanceM;
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
