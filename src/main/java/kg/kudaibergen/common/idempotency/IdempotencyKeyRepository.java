package kg.kudaibergen.common.idempotency;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

   @Modifying
   @Query("delete from IdempotencyKey k where k.createdAt < :before")
   int deleteOlderThan(@Param("before") Instant before);
}
