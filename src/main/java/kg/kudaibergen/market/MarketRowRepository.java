package kg.kudaibergen.market;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.market.entity.MarketRow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRowRepository extends JpaRepository<MarketRow, Long> {

   List<MarketRow> findByActiveTrueOrderBySortOrder();

   Optional<MarketRow> findByCode(String code);
}
