package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** «Сказали «есть», а не было» (08): покупатель отметил бокс, ответивший «Есть» на его запрос. Одна на ответ. */
@Entity
@Table(name = "false_have_marks")
public class FalseHaveMark {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "request_id", nullable = false, updatable = false)
   private Long requestId;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "buyer_id", nullable = false, updatable = false)
   private Long buyerId;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   protected FalseHaveMark() {
   }

   public FalseHaveMark(Long requestId, Long shopId, Long buyerId, Instant now) {
      this.requestId = requestId;
      this.shopId = shopId;
      this.buyerId = buyerId;
      this.createdAt = now;
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }
}
