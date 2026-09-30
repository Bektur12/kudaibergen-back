package kg.kudaibergen.master;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.master.entity.ServiceRecipient;
import kg.kudaibergen.master.entity.ServiceRecipientId;
import kg.kudaibergen.master.entity.ServiceRecipientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Лента мастера (39) и статистика заявки (37). Курсор ленты — (notified_at, request_id), по убыванию. */
public interface ServiceRecipientRepository extends JpaRepository<ServiceRecipient, ServiceRecipientId> {

   Optional<ServiceRecipient> findByRequestIdAndMasterId(Long requestId, Long masterId);

   @Query("select r.masterId from ServiceRecipient r where r.requestId = :requestId")
   Set<Long> findMasterIds(@Param("requestId") Long requestId);

   List<ServiceRecipient> findByRequestIdAndMasterIdIn(Long requestId, Collection<Long> masterIds);

   List<ServiceRecipient> findByRequestIdAndStatusOrderByRepliedAtAsc(Long requestId, ServiceRecipientStatus status);

   /** Счётчики статистики (37): получили, посмотрели, могут, не их профиль. */
   @Query(nativeQuery = true, value = """
         select count(*) as delivered,
                count(seen_at) as seen,
                count(*) filter (where status = 'CAN_HELP') as "canHelp",
                count(*) filter (where status = 'NOT_MINE') as "notMine"
         from service_recipients where request_id = :requestId""")
   ServiceCounts counts(@Param("requestId") Long requestId);

   interface ServiceCounts {
      long getDelivered();

      long getSeen();

      long getCanHelp();

      long getNotMine();
   }

   @Modifying(flushAutomatically = true)
   @Query(nativeQuery = true, value = """
         update service_recipients set status = 'EXPIRED'
         where request_id in (:requestIds) and status in ('DELIVERED', 'SEEN')""")
   int expire(@Param("requestIds") Collection<Long> requestIds);

   @Modifying(flushAutomatically = true)
   @Query(nativeQuery = true, value = """
         update service_recipients
         set status = case when seen_at is null then 'DELIVERED' else 'SEEN' end
         where request_id = :requestId and status = 'EXPIRED'""")
   int revive(@Param("requestId") Long requestId);

   /** Новые: заявка активна, мастер ещё не ответил. */
   @Query(nativeQuery = true, value = """
         select sr.* from service_recipients sr
         join service_requests r on r.id = sr.request_id
         where sr.master_id = :masterId and sr.replied_at is null and r.status = 'ACTIVE'
           and (sr.notified_at, sr.request_id) < (:at, :id)
         order by sr.notified_at desc, sr.request_id desc
         limit :limit""")
   List<ServiceRecipient> findNew(@Param("masterId") Long masterId, @Param("at") Instant at, @Param("id") long id,
                                  @Param("limit") int limit);

   /** «Вы откликнулись»: «Могу помочь», заявка не закрыта или закрыта с этим мастером. */
   @Query(nativeQuery = true, value = """
         select sr.* from service_recipients sr
         join service_requests r on r.id = sr.request_id
         where sr.master_id = :masterId and sr.status = 'CAN_HELP'
           and (r.status <> 'CLOSED' or r.closed_with_master_id = :masterId)
           and (sr.notified_at, sr.request_id) < (:at, :id)
         order by sr.notified_at desc, sr.request_id desc
         limit :limit""")
   List<ServiceRecipient> findAnswered(@Param("masterId") Long masterId, @Param("at") Instant at,
                                       @Param("id") long id, @Param("limit") int limit);

   /** «Истёкшие»: время вышло, мастер не ответил. */
   @Query(nativeQuery = true, value = """
         select sr.* from service_recipients sr
         where sr.master_id = :masterId and sr.status = 'EXPIRED'
           and (sr.notified_at, sr.request_id) < (:at, :id)
         order by sr.notified_at desc, sr.request_id desc
         limit :limit""")
   List<ServiceRecipient> findExpired(@Param("masterId") Long masterId, @Param("at") Instant at,
                                      @Param("id") long id, @Param("limit") int limit);
}
