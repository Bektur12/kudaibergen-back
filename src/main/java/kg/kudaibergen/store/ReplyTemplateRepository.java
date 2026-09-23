package kg.kudaibergen.store;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.store.entity.ReplyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {

   List<ReplyTemplate> findByStoreIdOrderBySortOrderAscIdAsc(Long storeId);

   Optional<ReplyTemplate> findByIdAndStoreId(Long id, Long storeId);
}
