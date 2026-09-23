package kg.kudaibergen.common.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

   @Id
   @Column(name = "key", length = 80)
   private String key;

   @Column(name = "user_id", nullable = false)
   private Long userId;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(name = "response", nullable = false)
   private String response;

   @Column(name = "created_at", nullable = false)
   private Instant createdAt = Instant.now();

   protected IdempotencyKey() {
   }

   public IdempotencyKey(String key, Long userId, String response) {
      this.key = key;
      this.userId = userId;
      this.response = response;
      this.createdAt = Instant.now();
   }

   public String getKey() {
      return key;
   }

   public Long getUserId() {
      return userId;
   }

   public String getResponse() {
      return response;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
