package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Запрос доставлен боксу: лента продавца (11), статистика запроса для покупателя (32) и статистика
 * бокса «без ответа», «время ответа» (17). Ряд и контейнер — на момент рассылки: бокс может переехать.
 */
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

   @Column(name = "row_id", updatable = false)
   private Long rowId;

   @Column(name = "container_id", updatable = false)
   private Long containerId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private RecipientStatus status = RecipientStatus.DELIVERED;

   @Column(name = "notified_at", nullable = false, updatable = false)
   private Instant notifiedAt;

   @Column(name = "seen_at")
   private Instant seenAt;

   @Column(name = "replied_at")
   private Instant repliedAt;

   protected RequestRecipient() {
   }

   public RequestRecipient(Long requestId, Long shopId, Long rowId, Long containerId, Instant notifiedAt) {
      this.requestId = requestId;
      this.shopId = shopId;
      this.rowId = rowId;
      this.containerId = containerId;
      this.notifiedAt = notifiedAt;
   }

   /** Продавец открыл карточку или нажал на пуш. Возвращает true, если это первый раз. */
   public boolean seen(Instant at) {
      if (seenAt != null) {
         return false;
      }
      seenAt = at;
      if (status == RecipientStatus.DELIVERED) {
         status = RecipientStatus.SEEN;
      }
      return true;
   }

   /** Ответ или его правка «Нет» ↔ «Есть». */
   public void answered(ReplyAnswer answer, Instant at) {
      seen(at);
      if (repliedAt == null) {
         repliedAt = at;
      }
      status = answer == ReplyAnswer.HAVE ? RecipientStatus.HAVE : RecipientStatus.NOT_HAVE;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getRowId() {
      return rowId;
   }

   public Long getContainerId() {
      return containerId;
   }

   public RecipientStatus getStatus() {
      return status;
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
