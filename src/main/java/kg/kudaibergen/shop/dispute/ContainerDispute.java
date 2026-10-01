package kg.kudaibergen.shop.dispute;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Спор за контейнер: «Это мой контейнер» от того, чьё место в приложении занято другим магазином. */
@Entity
@Table(name = "container_disputes")
public class ContainerDispute {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "container_id", nullable = false)
   private Long containerId;

   @Column(name = "claimant_user_id")
   private Long claimantUserId;

   @Column(name = "claimant_shop_id")
   private Long claimantShopId;

   @Column(name = "current_shop_id")
   private Long currentShopId;

   @Column(length = 1000)
   private String text;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private DisputeStatus status = DisputeStatus.OPEN;

   @Enumerated(EnumType.STRING)
   @Column(length = 8)
   private DisputeWinner winner;

   @Column(length = 1000)
   private String resolution;

   @Column(name = "resolved_by")
   private Long resolvedBy;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "resolved_at")
   private Instant resolvedAt;

   protected ContainerDispute() {
   }

   public ContainerDispute(Long containerId, Long claimantUserId, Long claimantShopId, Long currentShopId, String text) {
      this.containerId = containerId;
      this.claimantUserId = claimantUserId;
      this.claimantShopId = claimantShopId;
      this.currentShopId = currentShopId;
      this.text = text;
   }

   public void resolve(DisputeWinner winner, String resolution, Long adminId) {
      this.status = DisputeStatus.RESOLVED;
      this.winner = winner;
      this.resolution = resolution;
      this.resolvedBy = adminId;
      this.resolvedAt = Instant.now();
   }

   public boolean isOpen() {
      return status == DisputeStatus.OPEN;
   }

   public Long getId() {
      return id;
   }

   public Long getContainerId() {
      return containerId;
   }

   public Long getClaimantUserId() {
      return claimantUserId;
   }

   public Long getClaimantShopId() {
      return claimantShopId;
   }

   public Long getCurrentShopId() {
      return currentShopId;
   }

   public String getText() {
      return text;
   }

   public DisputeStatus getStatus() {
      return status;
   }

   public DisputeWinner getWinner() {
      return winner;
   }

   public String getResolution() {
      return resolution;
   }

   public Long getResolvedBy() {
      return resolvedBy;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getResolvedAt() {
      return resolvedAt;
   }
}
