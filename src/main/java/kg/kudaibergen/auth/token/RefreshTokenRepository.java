package kg.kudaibergen.auth.token;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

   Optional<RefreshToken> findByTokenHash(String tokenHash);

   /** Атомарно гасит токен; 0 — его уже погасил кто-то другой (повтор или кража). */
   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
   int revoke(@Param("id") Long id, @Param("now") Instant now);

   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
   int revokeAllOfUser(@Param("userId") Long userId, @Param("now") Instant now);

   @Modifying
   @Query("delete from RefreshToken t where t.expiresAt < :before")
   int deleteExpiredBefore(@Param("before") Instant before);
}
