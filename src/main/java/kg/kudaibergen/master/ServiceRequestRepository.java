package kg.kudaibergen.master;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

   /** Hibernate переводит таймаут -2 в FOR UPDATE SKIP LOCKED. */
   String SKIP_LOCKED = "-2";

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select r from ServiceRequest r where r.id = :id")
   Optional<ServiceRequest> findForUpdate(@Param("id") Long id);

   long countByBuyerIdAndStatus(Long buyerId, RequestStatus status);

   long countByBuyerIdAndCreatedAtAfter(Long buyerId, Instant after);

   /** «Мои заявки» (05б): активные сверху, потом остальные; внутри — новые первыми. Курсор — (ранг, id). */
   @Query("""
         select r from ServiceRequest r
         where r.buyerId = :buyerId
           and ((case when r.status = :active then 0 else 1 end) > :rank
             or ((case when r.status = :active then 0 else 1 end) = :rank and r.id < :beforeId))
         order by (case when r.status = :active then 0 else 1 end), r.id desc""")
   List<ServiceRequest> findMine(@Param("buyerId") Long buyerId, @Param("active") RequestStatus active,
                                 @Param("rank") int rank, @Param("beforeId") long beforeId, Pageable page);

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
   @Query("select r from ServiceRequest r where r.status = :active and r.expiresAt <= :now order by r.expiresAt")
   List<ServiceRequest> findExpiring(@Param("active") RequestStatus active, @Param("now") Instant now, Pageable page);
}
