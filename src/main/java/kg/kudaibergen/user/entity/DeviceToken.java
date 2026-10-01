package kg.kudaibergen.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** FCM-токен устройства. Токен уникален: при входе другим аккаунтом он переезжает к нему. */
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

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private Platform platform;

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   protected DeviceToken() {
   }

   public DeviceToken(Long userId, String token, Platform platform) {
      this.userId = userId;
      this.token = token;
      this.platform = platform;
   }

   public void reassign(Long userId, Platform platform) {
      this.userId = userId;
      this.platform = platform;
      this.updatedAt = Instant.now();
   }

   public Long getId() {
      return id;
   }

   public Long getUserId() {
      return userId;
   }

   public String getToken() {
      return token;
   }

   public Platform getPlatform() {
      return platform;
   }

   public Instant getUpdatedAt() {
      return updatedAt;
   }
}
