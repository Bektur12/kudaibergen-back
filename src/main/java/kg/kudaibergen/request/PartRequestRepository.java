package kg.kudaibergen.request;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PartRequestRepository extends JpaRepository<PartRequest, Long> {

   /** Hibernate переводит таймаут -2 в FOR UPDATE SKIP LOCKED. */
   String SKIP_LOCKED = "-2";

   /** Ответы, закрытие, продление и расширение меняют счётчики — по одному запросу за раз. */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select r from PartRequest r where r.id = :id")
   Optional<PartRequest> findForUpdate(@Param("id") Long id);

   long countByBuyerIdAndStatus(Long buyerId, RequestStatus status);

   long countByBuyerIdAndCreatedAtAfter(Long buyerId, Instant after);

   /** Самый старый запрос за сутки: когда он выйдет из окна, лимит освободится. */
   Optional<PartRequest> findFirstByBuyerIdAndCreatedAtAfterOrderByCreatedAtAsc(Long buyerId, Instant after);

   /**
    * «Мои запросы» (05): активные сверху, потом истёкшие и закрытые; внутри — новые первыми.
    * Курсор — (ранг, id) последней строки; ранг 0 у активных, 1 у остальных.
    */
   @Query("""
         select r from PartRequest r
         where r.buyerId = :buyerId
           and (:status is null or r.status = :status)
           and ((case when r.status = :active then 0 else 1 end) > :rank
             or ((case when r.status = :active then 0 else 1 end) = :rank and r.id < :beforeId))
         order by (case when r.status = :active then 0 else 1 end), r.id desc""")
   List<PartRequest> findMine(@Param("buyerId") Long buyerId, @Param("status") RequestStatus status,
                              @Param("active") RequestStatus active, @Param("rank") int rank,
                              @Param("beforeId") long beforeId, Pageable page);

   /**
    * Активные запросы, у которых вышло время. SKIP LOCKED — несколько экземпляров приложения
    * не возьмут одни и те же строки.
    */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
   @Query("select r from PartRequest r where r.status = :active and r.expiresAt <= :now order by r.expiresAt")
   List<PartRequest> findExpiring(@Param("active") RequestStatus active, @Param("now") Instant now, Pageable page);
}
