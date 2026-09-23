package kg.kudaibergen.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Одноразовый код входа. В БД лежит только хэш. */
@Entity
@Table(name = "sms_codes")
public class SmsCode {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, length = 20)
   private String phone;

   @Column(name = "code_hash", nullable = false, length = 80)
   private String codeHash;

   @Column(nullable = false)
   private short attempts;

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   @Column(name = "used_at")
   private Instant usedAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected SmsCode() {
   }

   public SmsCode(String phone, String codeHash, Instant expiresAt) {
      this.phone = phone;
      this.codeHash = codeHash;
      this.expiresAt = expiresAt;
      this.createdAt = Instant.now();
   }

   public boolean isExpired(Instant now) {
      return expiresAt.isBefore(now);
   }

   public boolean isUsed() {
      return usedAt != null;
   }

   public void markUsed() {
      this.usedAt = Instant.now();
   }

   public void registerAttempt() {
      this.attempts++;
   }

   public Long getId() {
      return id;
   }

   public String getPhone() {
      return phone;
   }

   public String getCodeHash() {
      return codeHash;
   }

   public short getAttempts() {
      return attempts;
   }

   public Instant getExpiresAt() {
      return expiresAt;
   }

   public Instant getUsedAt() {
      return usedAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
