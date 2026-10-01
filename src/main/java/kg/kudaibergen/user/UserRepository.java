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

   /** Регистрация без гонки: два параллельных входа одного нового номера не упадут на UNIQUE. */
   @Modifying
   @Query(nativeQuery = true, value = """
         insert into users (phone, lang) values (:phone, :lang) on conflict (phone) do nothing""")
   int insertIfAbsent(@Param("phone") String phone, @Param("lang") String lang);

   @Modifying
   @Query("delete from User u where u.deletionRequestedAt < :before")
   int deleteRequestedBefore(@Param("before") Instant before);

   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("update User u set u.lastSeenAt = :at where u.id = :id")
   void touchLastSeen(@Param("id") Long id, @Param("at") Instant at);
}
