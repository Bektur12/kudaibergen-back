package kg.kudaibergen.garage;

import kg.kudaibergen.garage.entity.CarModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarModelRepository extends JpaRepository<CarModel, Long> {
}
