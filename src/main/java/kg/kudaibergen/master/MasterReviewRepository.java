package kg.kudaibergen.master;

import java.util.List;

import kg.kudaibergen.master.entity.MasterReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterReviewRepository extends JpaRepository<MasterReview, Long> {

   @Query("select avg(r.stars) from MasterReview r where r.masterId = :masterId")
   Double averageStars(@Param("masterId") Long masterId);

   long countByMasterId(Long masterId);

   List<MasterReview> findByMasterIdOrderByCreatedAtDescIdDesc(Long masterId, Pageable page);
}
