package kg.kudaibergen.notification;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

   Optional<DeviceToken> findByToken(String token);

   List<DeviceToken> findByUserId(Long userId);

   @Query("select d.token from DeviceToken d where d.userId = :userId")
   List<String> findTokensByUserId(@Param("userId") Long userId);

   void deleteByTokenAndUserId(String token, Long userId);
}
