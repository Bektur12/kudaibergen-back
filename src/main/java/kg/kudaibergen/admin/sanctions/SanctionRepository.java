package kg.kudaibergen.admin.sanctions;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {

   List<Sanction> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(SanctionTarget targetType, Long targetId,
                                                                       Pageable page);

   @Query("""
         select distinct s.targetId from Sanction s
         where s.targetType = :target and s.targetId in :ids
           and s.type = kg.kudaibergen.admin.sanctions.SanctionType.WARNING and s.activeUntil > :now""")
   List<Long> findWarned(@Param("target") SanctionTarget target, @Param("ids") Collection<Long> ids,
                         @Param("now") Instant now);

   @Query("""
         select count(s) from Sanction s
         where s.targetType = :target and s.targetId = :id
           and s.type = kg.kudaibergen.admin.sanctions.SanctionType.WARNING and s.createdAt > :since""")
   long countWarningsSince(@Param("target") SanctionTarget target, @Param("id") Long id,
                           @Param("since") Instant since);
}
