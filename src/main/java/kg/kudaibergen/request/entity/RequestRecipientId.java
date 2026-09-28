package kg.kudaibergen.request.entity;

import java.io.Serializable;
import java.util.Objects;

public class RequestRecipientId implements Serializable {

   private Long requestId;
   private Long shopId;

   protected RequestRecipientId() {
   }

   public RequestRecipientId(Long requestId, Long shopId) {
      this.requestId = requestId;
      this.shopId = shopId;
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof RequestRecipientId id
            && Objects.equals(requestId, id.requestId) && Objects.equals(shopId, id.shopId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(requestId, shopId);
   }
}
