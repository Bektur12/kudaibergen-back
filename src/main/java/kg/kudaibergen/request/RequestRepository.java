package kg.kudaibergen.request;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import kg.kudaibergen.request.dto.SellerRequestRow;
import kg.kudaibergen.request.entity.Request;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, Long> {

   Page<Request> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

   Optional<Request> findByIdAndBuyerId(Long id, Long buyerId);

   /** Лимит «10 запросов в сутки на покупателя» — иначе спамеры завалят продавцов. */
   int countByBuyerIdAndCreatedAtAfter(Long buyerId, Instant after);

   @Query("""
         select r from Request r
         where r.id in :ids
           and r.status = kg.kudaibergen.request.entity.RequestStatus.ACTIVE
           and r.expiresAt > :now
         """)
   List<Request> findActiveByIds(@Param("ids") List<Long> ids, @Param("now") Instant now);

   // атомарный инкремент: два продавца могут отвечать одновременно
   @Modifying(flushAutomatically = true)
   @Query("update Request r set r.offerCount = r.offerCount + 1 where r.id = :id")
   void incrementOfferCount(@Param("id") Long id);

   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("""
         update Request r
         set r.status = kg.kudaibergen.request.entity.RequestStatus.EXPIRED
         where r.status = kg.kudaibergen.request.entity.RequestStatus.ACTIVE
           and r.expiresAt < :now
         """)
   int expireWhereExpiresAtBefore(@Param("now") Instant now);

   /**
    * Лента продавца: активные запросы, которые пришли именно этому магазину,
    * вместе с отметками «просмотрел / ответил».
    */
   @Query(value = """
         select new kg.kudaibergen.request.dto.SellerRequestRow(
               r.id, r.category, r.description, r.carText, r.budgetMin, r.budgetMax, r.currency,
               r.city, r.urgent, r.offerCount, r.createdAt, r.expiresAt, rr.seenAt, rr.repliedAt)
         from Request r
         join RequestRecipient rr on rr.id.requestId = r.id
         where rr.id.storeId = :storeId
           and r.status = kg.kudaibergen.request.entity.RequestStatus.ACTIVE
           and (:onlyUrgent = false or r.urgent = true)
           and (:onlyUnanswered = false or rr.repliedAt is null)
         order by r.urgent desc, r.createdAt desc
         """,
         countQuery = """
               select count(r) from Request r
               join RequestRecipient rr on rr.id.requestId = r.id
               where rr.id.storeId = :storeId
                 and r.status = kg.kudaibergen.request.entity.RequestStatus.ACTIVE
                 and (:onlyUrgent = false or r.urgent = true)
                 and (:onlyUnanswered = false or rr.repliedAt is null)
               """)
   Page<SellerRequestRow> findForStore(@Param("storeId") Long storeId,
                                       @Param("onlyUrgent") boolean onlyUrgent,
                                       @Param("onlyUnanswered") boolean onlyUnanswered,
                                       Pageable pageable);
}
