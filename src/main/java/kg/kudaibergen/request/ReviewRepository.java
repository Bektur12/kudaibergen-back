package kg.kudaibergen.request;

import java.util.List;

import kg.kudaibergen.request.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

   /** null — отзывов нет. */
   @Query("select avg(r.stars) from Review r where r.shopId = :shopId and r.hiddenByAdmin = false")
   Double averageStars(@Param("shopId") Long shopId);

   /** Отзывы, видимые покупателям (скрытые администрацией не считаются). */
   @Query("select count(r) from Review r where r.shopId = :shopId and r.hiddenByAdmin = false")
   long countByShopId(@Param("shopId") Long shopId);

   /** Отзывы магазина (30, 21): новые сверху, курсор — id. */
   @Query("select r from Review r where r.shopId = :shopId and r.hiddenByAdmin = false and r.id < :beforeId order by r.id desc")
   List<Review> findPage(@Param("shopId") Long shopId, @Param("beforeId") long beforeId, Pageable page);
}
