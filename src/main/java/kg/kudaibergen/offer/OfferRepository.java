package kg.kudaibergen.offer;

import java.util.List;

import kg.kudaibergen.offer.entity.Offer;
import kg.kudaibergen.offer.entity.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

   List<Offer> findByRequestIdOrderByCreatedAtAsc(Long requestId);

   /** Один магазин — одно живое предложение на запрос. */
   @Query("""
         select count(o) > 0 from Offer o
         where o.requestId = :requestId and o.storeId = :storeId
           and o.status = kg.kudaibergen.offer.entity.OfferStatus.ACTIVE
         """)
   boolean existsActive(@Param("requestId") Long requestId, @Param("storeId") Long storeId);

   /** Запрос протух — активные предложения по нему тоже. */
   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("""
         update Offer o set o.status = kg.kudaibergen.offer.entity.OfferStatus.EXPIRED
         where o.status = kg.kudaibergen.offer.entity.OfferStatus.ACTIVE
           and o.requestId in (
               select r.id from Request r
               where r.status = kg.kudaibergen.request.entity.RequestStatus.EXPIRED)
         """)
   int expireForExpiredRequests();

   long countByStoreIdAndStatus(Long storeId, OfferStatus status);
}
