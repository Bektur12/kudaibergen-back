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

   /** Ответы, закрытие и расширение меняют счётчики — по одному запросу за раз. */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select r from PartRequest r where r.id = :id")
   Optional<PartRequest> findForUpdate(@Param("id") Long id);

   long countByBuyerIdAndStatus(Long buyerId, RequestStatus status);

   long countByBuyerIdAndCreatedAtAfter(Long buyerId, Instant after);

   /** Самый старый запрос за сутки: когда он выйдет из окна, лимит освободится. */
   Optional<PartRequest> findFirstByBuyerIdAndCreatedAtAfterOrderByCreatedAtAsc(Long buyerId, Instant after);

   /**
    * «Мои запросы» (05): открытые сверху, потом остальные; внутри — новые первыми.
    * Курсор — (ранг, id) последней строки; ранг 0 у открытых, 1 у закрытых.
    */
   @Query("""
         select r from PartRequest r
         where r.buyerId = :buyerId
           and (:status is null or r.status = :status)
           and ((case when r.status = :open then 0 else 1 end) > :rank
             or ((case when r.status = :open then 0 else 1 end) = :rank and r.id < :beforeId))
         order by (case when r.status = :open then 0 else 1 end), r.id desc""")
   List<PartRequest> findMine(@Param("buyerId") Long buyerId, @Param("status") RequestStatus status,
                              @Param("open") RequestStatus open, @Param("rank") int rank,
                              @Param("beforeId") long beforeId, Pageable page);

   /**
    * Открытые запросы без «Есть», по которым 30 минут никто не ответил и покупателю ещё не сказали.
    * SKIP LOCKED — несколько экземпляров приложения не возьмут одни и те же строки.
    */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
   @Query("""
         select r from PartRequest r
         where r.status = :open and r.haveCount = 0 and r.noReplyAt is null and r.sentAt < :before
         order by r.id""")
   List<PartRequest> findNoReplyDue(@Param("open") RequestStatus open, @Param("before") Instant before,
                                    Pageable page);

   /** Открытые запросы без действий дольше срока — истекают. */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
   @Query("select r from PartRequest r where r.status = :open and r.lastActivityAt < :before order by r.id")
   List<PartRequest> findIdle(@Param("open") RequestStatus open, @Param("before") Instant before, Pageable page);
}
