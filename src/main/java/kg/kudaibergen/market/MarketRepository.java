package kg.kudaibergen.market;

import java.util.List;

import kg.kudaibergen.market.entity.Bazaar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Bazaar, Long> {

   List<Bazaar> findByActiveTrueOrderBySortOrder();
}
