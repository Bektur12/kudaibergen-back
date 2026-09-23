package kg.kudaibergen.request.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RequestRecipientId implements Serializable {

   @Column(name = "request_id", nullable = false)
   private Long requestId;

   @Column(name = "store_id", nullable = false)
   private Long storeId;

   protected RequestRecipientId() {
   }

   public RequestRecipientId(Long requestId, Long storeId) {
      this.requestId = requestId;
      this.storeId = storeId;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getStoreId() {
      return storeId;
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      }
      if (!(other instanceof RequestRecipientId that)) {
         return false;
      }
      return Objects.equals(requestId, that.requestId) && Objects.equals(storeId, that.storeId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(requestId, storeId);
   }
}
