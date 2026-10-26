package kg.kudaibergen.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select o from DeliveryOrder o where o.id = :id")
   Optional<DeliveryOrder> findForUpdate(@Param("id") Long id);

   /** Заказы покупателя, новые сверху; statuses — какие показать. */
   @Query("""
         select o from DeliveryOrder o
         where o.buyerId = :buyerId and o.status in :statuses and o.id < :beforeId
         order by o.id desc""")
   List<DeliveryOrder> findOfBuyer(@Param("buyerId") Long buyerId, @Param("statuses") Collection<DeliveryStatus> statuses,
                                   @Param("beforeId") long beforeId, Pageable page);

   @Query("""
         select o from DeliveryOrder o
         where o.shopId = :shopId and o.status in :statuses and o.id < :beforeId
         order by o.id desc""")
   List<DeliveryOrder> findOfShop(@Param("shopId") Long shopId, @Param("statuses") Collection<DeliveryStatus> statuses,
                                  @Param("beforeId") long beforeId, Pageable page);
}
