package kg.kudaibergen.request;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestReplyRepository extends JpaRepository<RequestReply, Long> {

   Optional<RequestReply> findByRequestIdAndShopId(Long requestId, Long shopId);

   boolean existsByRequestIdAndShopIdAndAnswer(Long requestId, Long shopId, ReplyAnswer answer);

   /** Экран 07: «Есть» в порядке прихода; afterId — только новые с прошлого опроса. */
   @Query("""
         select r from RequestReply r
         where r.requestId = :requestId and r.answer = :have and r.id > :afterId
         order by r.id""")
   List<RequestReply> findHave(@Param("requestId") Long requestId, @Param("have") ReplyAnswer have,
                               @Param("afterId") long afterId);

   List<RequestReply> findByShopIdAndRequestIdIn(Long shopId, Collection<Long> requestIds);

   /** «от 2 900 с» в «Мои запросы» (03): самая низкая цена «Есть» по каждому запросу. */
   @Query("""
         select r.requestId as requestId, min(r.price) as price from RequestReply r
         where r.requestId in :requestIds and r.answer = :have and r.price is not null
         group by r.requestId""")
   List<MinPrice> minPrices(@Param("requestIds") Collection<Long> requestIds, @Param("have") ReplyAnswer have);

   interface MinPrice {
      Long getRequestId();

      Integer getPrice();
   }
}
