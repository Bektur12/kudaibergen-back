package kg.kudaibergen.review;

import kg.kudaibergen.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

   Page<Review> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, String status, Pageable pageable);

   boolean existsByAuthorIdAndStoreId(Long authorId, Long storeId);

   @Query("""
         select coalesce(avg(cast(r.rating as double)), 0) as average, count(r) as total
         from Review r where r.storeId = :storeId and r.status = 'APPROVED'
         """)
   RatingAggregate aggregate(@Param("storeId") Long storeId);

   /** Среднее и количество одобренных отзывов магазина. */
   interface RatingAggregate {

      Double getAverage();

      Long getTotal();
   }
}
