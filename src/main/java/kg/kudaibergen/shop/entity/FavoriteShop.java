package kg.kudaibergen.shop.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Сердечко на профиле магазина; список «Избранные магазины» в профиле (19). */
@Entity
@Table(name = "favorite_shops")
@IdClass(FavoriteShop.Key.class)
public class FavoriteShop {

   @Id
   @Column(name = "user_id")
   private Long userId;

   @Id
   @Column(name = "shop_id")
   private Long shopId;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected FavoriteShop() {
   }

   public FavoriteShop(Long userId, Long shopId) {
      this.userId = userId;
      this.shopId = shopId;
   }

   public Long getUserId() {
      return userId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public static class Key implements java.io.Serializable {

      private Long userId;
      private Long shopId;

      protected Key() {
      }

      public Key(Long userId, Long shopId) {
         this.userId = userId;
         this.shopId = shopId;
      }

      @Override
      public boolean equals(Object other) {
         return other instanceof Key key && java.util.Objects.equals(userId, key.userId)
               && java.util.Objects.equals(shopId, key.shopId);
      }

      @Override
      public int hashCode() {
         return java.util.Objects.hash(userId, shopId);
      }
   }
}
