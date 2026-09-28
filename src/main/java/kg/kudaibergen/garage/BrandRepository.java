package kg.kudaibergen.garage;

import kg.kudaibergen.garage.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
}
