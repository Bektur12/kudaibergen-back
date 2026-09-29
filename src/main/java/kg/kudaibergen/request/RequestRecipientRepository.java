package kg.kudaibergen.request;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.request.entity.RecipientStatus;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Лента продавца (11) и статистика запроса (32). Курсор ленты — (notified_at, request_id) последней строки,
 * сортировка по убыванию. Закрытый покупателем запрос остаётся только у бокса, с которым закрыли.
 */
public interface RequestRecipientRepository extends JpaRepository<RequestRecipient, RequestRecipientId> {

   Optional<RequestRecipient> findByRequestIdAndShopId(Long requestId, Long shopId);

   @Query("select rr.shopId from RequestRecipient rr where rr.requestId = :requestId")
   Set<Long> findShopIds(@Param("requestId") Long requestId);

   long countByRequestIdAndSeenAtIsNotNull(Long requestId);

   /** Списки «Есть» и «Нет» статистики (32) — по времени ответа. */
   List<RequestRecipient> findByRequestIdAndStatusOrderByRepliedAtAsc(Long requestId, RecipientStatus status);

   /** Счётчики статистики (32): доставлено, открыли, «Есть», «Нет». */
   @Query(nativeQuery = true, value = """
         select count(*) as delivered,
                count(seen_at) as seen,
                count(*) filter (where status = 'HAVE') as have,
                count(*) filter (where status = 'NOT_HAVE') as "notHave"
         from request_recipients where request_id = :requestId""")
   Counts counts(@Param("requestId") Long requestId);

   interface Counts {
      long getDelivered();

      long getSeen();

      long getHave();

      long getNotHave();
   }

   /** Время вышло: у продавцов без ответа запрос уходит в «Истёкшие». */
   @Modifying(flushAutomatically = true)
   @Query(nativeQuery = true, value = """
         update request_recipients set status = 'EXPIRED'
         where request_id in (:requestIds) and status in ('DELIVERED', 'SEEN')""")
   int expire(@Param("requestIds") Collection<Long> requestIds);

   /** Продлили или расширили истёкший запрос — продавцы без ответа снова могут ответить. */
   @Modifying(flushAutomatically = true)
   @Query(nativeQuery = true, value = """
         update request_recipients
         set status = case when seen_at is null then 'DELIVERED' else 'SEEN' end
         where request_id = :requestId and status = 'EXPIRED'""")
   int revive(@Param("requestId") Long requestId);

   /** Новые: запрос активен, бокс ещё не ответил. */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         join part_requests r on r.id = rr.request_id
         where rr.shop_id = :shopId and rr.replied_at is null and r.status = 'ACTIVE'
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findNew(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                                  @Param("limit") int limit);

   /** «Вы ответили «есть»»: запрос не закрыт или закрыт с этим боксом. «Нет» скрываются. */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         join part_requests r on r.id = rr.request_id
         where rr.shop_id = :shopId and rr.status = 'HAVE'
           and (r.status <> 'CLOSED' or r.closed_with_shop_id = :shopId)
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findAnswered(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                                       @Param("limit") int limit);

   /** «Истёкшие»: время вышло, бокс не ответил; покупатель ещё может продлить. */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         where rr.shop_id = :shopId and rr.status = 'EXPIRED'
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findExpired(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                                      @Param("limit") int limit);

   /**
    * Без ответа: время вышло или запрос закрыт, а бокс не ответил ни «Есть», ни «Нет» —
    * «Смотреть» из статистики (17). Активные без ответа — в NEW.
    */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         join part_requests r on r.id = rr.request_id
         where rr.shop_id = :shopId and rr.replied_at is null and r.status <> 'ACTIVE'
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findUnanswered(@Param("shopId") Long shopId, @Param("at") Instant at,
                                         @Param("id") long id, @Param("limit") int limit);
}
