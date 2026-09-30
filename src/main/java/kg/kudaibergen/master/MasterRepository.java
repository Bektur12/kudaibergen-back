package kg.kudaibergen.master;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterRepository extends JpaRepository<Master, Long> {

   Optional<Master> findByOwnerId(Long ownerId);

   @Query("select m.id from Master m where m.publicId = :publicId")
   Optional<Long> findIdByPublicId(@Param("publicId") String publicId);

   /**
    * Кому может уйти заявка: действующий мастер, «Принимаю» включено, услуга в его списке.
    * Марку, страну, расстояние и часы досматривает ServiceRecipientFinder (мастеров — сотни).
    */
   @Query("select m from Master m where m.status = :status and m.accepting = true and :service member of m.services")
   List<Master> findReceiving(@Param("status") ShopStatus status, @Param("service") String service);
}
