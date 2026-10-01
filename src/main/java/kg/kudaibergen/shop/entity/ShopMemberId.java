package kg.kudaibergen.shop.entity;

import java.io.Serializable;
import java.util.Objects;

public class ShopMemberId implements Serializable {

   private Long shopId;
   private Long userId;

   protected ShopMemberId() {
   }

   public ShopMemberId(Long shopId, Long userId) {
      this.shopId = shopId;
      this.userId = userId;
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof ShopMemberId id && Objects.equals(shopId, id.shopId) && Objects.equals(userId, id.userId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(shopId, userId);
   }
}
