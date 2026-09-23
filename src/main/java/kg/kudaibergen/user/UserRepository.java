package kg.kudaibergen.user;

import java.time.Instant;
import java.util.Optional;

import kg.kudaibergen.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

   Optional<User> findByPhone(String phone);

   boolean existsByPhone(String phone);

   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("update User u set u.lastSeenAt = :at where u.id = :id")
   void touchLastSeen(@Param("id") Long id, @Param("at") Instant at);

   @Query("select u.lastSeenAt from User u where u.id = :id")
   Instant findLastSeenAt(@Param("id") Long id);
}
