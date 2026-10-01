package kg.kudaibergen.shop.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Человек в боксе. Один человек — не больше одного магазина. */
@Entity
@Table(name = "shop_members")
@IdClass(ShopMemberId.class)
public class ShopMember {

   @Id
   @Column(name = "shop_id")
   private Long shopId;

   @Id
   @Column(name = "user_id")
   private Long userId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 5)
   private MemberRole role;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected ShopMember() {
   }

   public ShopMember(Long shopId, Long userId, MemberRole role) {
      this.shopId = shopId;
      this.userId = userId;
      this.role = role;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getUserId() {
      return userId;
   }

   public MemberRole getRole() {
      return role;
   }

   public boolean isOwner() {
      return role == MemberRole.OWNER;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
