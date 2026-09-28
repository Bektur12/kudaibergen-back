package kg.kudaibergen.market;

import java.util.List;

import kg.kudaibergen.market.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerRepository extends JpaRepository<Container, Long> {

   List<Container> findByActiveTrue();

   List<Container> findByRowId(Long rowId);
}
