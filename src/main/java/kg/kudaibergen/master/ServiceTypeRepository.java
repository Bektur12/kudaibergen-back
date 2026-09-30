package kg.kudaibergen.master;

import java.util.List;

import kg.kudaibergen.master.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, String> {

   List<ServiceType> findAllByOrderBySortOrderAsc();
}
