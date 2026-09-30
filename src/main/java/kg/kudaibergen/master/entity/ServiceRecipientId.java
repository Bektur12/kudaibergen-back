package kg.kudaibergen.master.entity;

import java.io.Serializable;
import java.util.Objects;

public class ServiceRecipientId implements Serializable {

   private Long requestId;
   private Long masterId;

   protected ServiceRecipientId() {
   }

   public ServiceRecipientId(Long requestId, Long masterId) {
      this.requestId = requestId;
      this.masterId = masterId;
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof ServiceRecipientId id
            && Objects.equals(requestId, id.requestId) && Objects.equals(masterId, id.masterId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(requestId, masterId);
   }
}
