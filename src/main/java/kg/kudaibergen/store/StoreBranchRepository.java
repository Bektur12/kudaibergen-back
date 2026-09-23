package kg.kudaibergen.store;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.store.entity.StoreBranch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreBranchRepository extends JpaRepository<StoreBranch, Long> {

   List<StoreBranch> findByStoreIdOrderByIdAsc(Long storeId);

   List<StoreBranch> findByStoreIdInOrderByIdAsc(List<Long> storeIds);

   Optional<StoreBranch> findByIdAndStoreId(Long id, Long storeId);
}
