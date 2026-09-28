package kg.kudaibergen.request;

import kg.kudaibergen.request.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

   /** null — отзывов нет. */
   @Query("select avg(r.stars) from Review r where r.shopId = :shopId")
   Double averageStars(@Param("shopId") Long shopId);

   long countByShopId(Long shopId);
}
