package kg.kudaibergen.market;

import java.util.Optional;

import kg.kudaibergen.market.entity.MapVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MapVersionRepository extends JpaRepository<MapVersion, Long> {

   Optional<MapVersion> findByCurrentTrue();

   @Query("select coalesce(max(v.version), 0) from MapVersion v")
   int maxVersion();

   @Modifying(flushAutomatically = true)
   @Query("update MapVersion v set v.current = false where v.current = true")
   void clearCurrent();
}
