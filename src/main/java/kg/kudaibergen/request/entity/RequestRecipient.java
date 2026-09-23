package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Кому улетел запрос и что с ним стало у конкретного продавца.
 * Из этой таблицы считается вся аналитика и рейтинг скорости ответа.
 */
@Entity
@Table(name = "request_recipients")
public class RequestRecipient {

   @EmbeddedId
   private RequestRecipientId id;

   @Column(name = "notified_at", nullable = false)
   private Instant notifiedAt = Instant.now();

   @Column(name = "seen_at")
   private Instant seenAt;

   @Column(name = "replied_at")
   private Instant repliedAt;

   protected RequestRecipient() {
   }

   public RequestRecipient(Long requestId, Long storeId) {
      this.id = new RequestRecipientId(requestId, storeId);
      this.notifiedAt = Instant.now();
   }

   public void markSeen() {
      if (seenAt == null) {
         seenAt = Instant.now();
      }
   }

   public void markReplied(Instant when) {
      this.repliedAt = when;
      markSeen();
   }

   public RequestRecipientId getId() {
      return id;
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
