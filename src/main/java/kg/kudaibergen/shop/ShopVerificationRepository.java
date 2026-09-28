package kg.kudaibergen.shop;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.shop.entity.ShopVerification;
import kg.kudaibergen.shop.entity.VerificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopVerificationRepository extends JpaRepository<ShopVerification, Long> {

   Optional<ShopVerification> findFirstByShopIdOrderByCreatedAtDescIdDesc(Long shopId);

   Optional<ShopVerification> findFirstByShopIdAndContainerIdAndStatusOrderByCreatedAtDesc(
         Long shopId, Long containerId, VerificationStatus status);

   /** Очередь админки «подтвердите меня». */
   List<ShopVerification> findByStatusOrderByCreatedAtAsc(VerificationStatus status, Pageable page);
}
