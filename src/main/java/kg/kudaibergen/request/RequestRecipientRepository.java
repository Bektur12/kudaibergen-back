package kg.kudaibergen.request;

import java.util.Optional;

import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRecipientRepository extends JpaRepository<RequestRecipient, RequestRecipientId> {

   @Query("select rr from RequestRecipient rr where rr.id.requestId = :requestId and rr.id.storeId = :storeId")
   Optional<RequestRecipient> find(@Param("requestId") Long requestId, @Param("storeId") Long storeId);

   @Query("""
         select count(rr) > 0 from RequestRecipient rr
         where rr.id.requestId = :requestId and rr.id.storeId = :storeId
         """)
   boolean exists(@Param("requestId") Long requestId, @Param("storeId") Long storeId);
}
