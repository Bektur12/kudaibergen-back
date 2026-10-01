package kg.kudaibergen.master;

import java.util.List;

import kg.kudaibergen.master.entity.MasterReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterReviewRepository extends JpaRepository<MasterReview, Long> {

   @Query("select avg(r.stars) from MasterReview r where r.masterId = :masterId and r.hiddenByAdmin = false")
   Double averageStars(@Param("masterId") Long masterId);

   @Query("select count(r) from MasterReview r where r.masterId = :masterId and r.hiddenByAdmin = false")
   long countByMasterId(@Param("masterId") Long masterId);

   @Query("""
         select r from MasterReview r where r.masterId = :masterId and r.hiddenByAdmin = false
         order by r.createdAt desc, r.id desc""")
   List<MasterReview> findByMasterIdOrderByCreatedAtDescIdDesc(@Param("masterId") Long masterId, Pageable page);
}
