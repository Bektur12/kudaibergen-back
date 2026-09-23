package kg.kudaibergen.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "user_id", nullable = false)
   private Long userId;

   @Column(nullable = false, unique = true, length = 255)
   private String token;

   @Column(nullable = false, length = 10)
   private String platform;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected DeviceToken() {
   }

   public DeviceToken(Long userId, String token, String platform) {
      this.userId = userId;
      this.token = token;
      this.platform = platform;
   }

   public Long getId() {
      return id;
   }

   public Long getUserId() {
      return userId;
   }

   public void setUserId(Long userId) {
      this.userId = userId;
   }

   public String getToken() {
      return token;
   }

   public String getPlatform() {
      return platform;
   }

   public void setPlatform(String platform) {
      this.platform = platform;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
