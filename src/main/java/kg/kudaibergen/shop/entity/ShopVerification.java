package kg.kudaibergen.shop.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Попытка подтвердить, что магазин стоит в контейнере. GPS не хранится — только итог. */
@Entity
@Table(name = "shop_verifications")
public class ShopVerification {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "shop_id", nullable = false)
   private Long shopId;

   @Column(name = "container_id", nullable = false)
   private Long containerId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 5)
   private VerificationMethod method;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private VerificationStatus status = VerificationStatus.PENDING;

   @Column(length = 300)
   private String reason;

   @Column(name = "decided_by")
   private Long decidedBy;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "decided_at")
   private Instant decidedAt;

   protected ShopVerification() {
   }

   public ShopVerification(Long shopId, Long containerId, VerificationMethod method) {
      this.shopId = shopId;
      this.containerId = containerId;
      this.method = method;
   }

   public void approve(Long decidedBy) {
      decide(VerificationStatus.APPROVED, decidedBy, null);
   }

   public void reject(Long decidedBy, String reason) {
      decide(VerificationStatus.REJECTED, decidedBy, reason);
   }

   private void decide(VerificationStatus status, Long decidedBy, String reason) {
      this.status = status;
      this.decidedBy = decidedBy;
      this.reason = reason;
      this.decidedAt = Instant.now();
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getContainerId() {
      return containerId;
   }

   public VerificationMethod getMethod() {
      return method;
   }

   public VerificationStatus getStatus() {
      return status;
   }

   public String getReason() {
      return reason;
   }

   public Long getDecidedBy() {
      return decidedBy;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getDecidedAt() {
      return decidedAt;
   }
}
