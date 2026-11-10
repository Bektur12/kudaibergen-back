package kg.kudaibergen.market;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.market.entity.MarketRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketRowRepository extends JpaRepository<MarketRow, Long> {

   List<MarketRow> findByActiveTrueOrderBySortOrder();

   Optional<MarketRow> findByCode(String code);

   /** Ряд по коду без учёта регистра; при дублях — активный первым. */
   @Query("select r from MarketRow r where lower(r.code) = lower(:code) order by r.active desc, r.id")
   List<MarketRow> findByCodeIgnoreCase(@org.springframework.data.repository.query.Param("code") String code);

   @Query("select max(r.sortOrder) from MarketRow r")
   Short maxSortOrder();
}
