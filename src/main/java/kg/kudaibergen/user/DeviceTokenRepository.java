package kg.kudaibergen.user;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.user.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

   Optional<DeviceToken> findByToken(String token);

   List<DeviceToken> findByUserId(Long userId);

   @Modifying
   @Query("delete from DeviceToken d where d.token = :token and d.userId = :userId")
   int deleteOwned(@Param("token") String token, @Param("userId") Long userId);

   @Modifying
   @Query("delete from DeviceToken d where d.userId = :userId")
   int deleteAllOfUser(@Param("userId") Long userId);

   /** FCM сообщил, что токены больше не действуют. */
   @Modifying
   @Query("delete from DeviceToken d where d.token in :tokens")
   int deleteByTokens(@Param("tokens") List<String> tokens);
}
