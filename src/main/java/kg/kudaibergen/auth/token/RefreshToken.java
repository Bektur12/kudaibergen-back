package kg.kudaibergen.auth.token;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Непрозрачный refresh-токен. В БД только SHA-256 от него. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "user_id", nullable = false)
   private Long userId;

   @Column(name = "token_hash", nullable = false, unique = true, length = 64)
   private String tokenHash;

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   @Column(name = "revoked_at")
   private Instant revokedAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected RefreshToken() {
   }

   public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
      this.userId = userId;
      this.tokenHash = tokenHash;
      this.expiresAt = expiresAt;
   }

   public boolean isExpired(Instant now) {
      return expiresAt.isBefore(now);
   }

   public boolean isRevoked() {
      return revokedAt != null;
   }

   public Long getId() {
      return id;
   }

   public Long getUserId() {
      return userId;
   }

   public String getTokenHash() {
      return tokenHash;
   }

   public Instant getExpiresAt() {
      return expiresAt;
   }

   public Instant getRevokedAt() {
      return revokedAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
