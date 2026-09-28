package kg.kudaibergen.request;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Лента продавца (11). Курсор — (notified_at, request_id) последней строки, сортировка по убыванию.
 * Закрытый покупателем запрос остаётся только у бокса, с которым закрыли.
 */
public interface RequestRecipientRepository extends JpaRepository<RequestRecipient, RequestRecipientId> {

   Optional<RequestRecipient> findByRequestIdAndShopId(Long requestId, Long shopId);

   @Query("select rr.shopId from RequestRecipient rr where rr.requestId = :requestId")
   Set<Long> findShopIds(@Param("requestId") Long requestId);

   long countByRequestIdAndSeenAtIsNotNull(Long requestId);

   /** Новые: запрос открыт, бокс ещё не ответил. */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         join part_requests r on r.id = rr.request_id
         where rr.shop_id = :shopId and rr.replied_at is null and r.status = 'OPEN'
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findNew(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                                  @Param("limit") int limit);

   /** «Вы ответили «есть»»: запрос открыт или закрыт с этим боксом. «Нет» скрываются. */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         join part_requests r on r.id = rr.request_id
         join request_replies rep on rep.request_id = rr.request_id and rep.shop_id = rr.shop_id
         where rr.shop_id = :shopId and rep.answer = 'HAVE'
           and (r.status = 'OPEN' or r.closed_with_shop_id = :shopId)
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findAnswered(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                                       @Param("limit") int limit);

   /** Без ответа — ни «Есть», ни «Нет»; «Смотреть» из статистики (17). */
   @Query(nativeQuery = true, value = """
         select rr.* from request_recipients rr
         where rr.shop_id = :shopId and rr.replied_at is null
           and (rr.notified_at, rr.request_id) < (:at, :id)
         order by rr.notified_at desc, rr.request_id desc
         limit :limit""")
   List<RequestRecipient> findUnanswered(@Param("shopId") Long shopId, @Param("at") Instant at,
                                         @Param("id") long id, @Param("limit") int limit);
}
