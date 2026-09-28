package kg.kudaibergen.garage;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.garage.entity.Car;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarRepository extends JpaRepository<Car, Long> {

   /** Основная первой, остальные — в порядке добавления (экран 04). */
   @Query("select c from Car c where c.userId = :userId order by c.primary desc, c.createdAt asc, c.id asc")
   List<Car> findGarage(@Param("userId") Long userId);

   Optional<Car> findByIdAndUserId(Long id, Long userId);

   Optional<Car> findByUserIdAndPrimaryTrue(Long userId);

   long countByUserId(Long userId);

   /** Основная машина одна: перед назначением новой снимаем флаг со старой. */
   @Modifying(flushAutomatically = true)
   @Query("update Car c set c.primary = false where c.userId = :userId and c.primary = true")
   void clearPrimary(@Param("userId") Long userId);
}
