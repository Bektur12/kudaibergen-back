package kg.kudaibergen.user;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.user.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

   List<Vehicle> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

   Optional<Vehicle> findByIdAndUserId(Long id, Long userId);

   /** Машина по умолчанию одна: перед установкой новой снимаем флаг со старой. */
   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("update Vehicle v set v.isDefault = false where v.user.id = :userId and v.isDefault = true")
   void clearDefault(@Param("userId") Long userId);
}
