package kg.kudaibergen.request;

import java.time.Instant;

import kg.kudaibergen.request.entity.FalseHaveMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FalseHaveMarkRepository extends JpaRepository<FalseHaveMark, Long> {

   boolean existsByRequestIdAndShopId(Long requestId, Long shopId);

   /** Сколько разных покупателей отметили бокс после since: три — пауза. */
   @Query("select count(distinct m.buyerId) from FalseHaveMark m where m.shopId = :shopId and m.createdAt > :since")
   long countBuyersSince(@Param("shopId") Long shopId, @Param("since") Instant since);

   /** «0 «не было»» за 30 дней в «Мой бокс» (15). */
   long countByShopIdAndCreatedAtAfter(Long shopId, Instant since);
}
