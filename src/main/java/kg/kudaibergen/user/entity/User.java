package kg.kudaibergen.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true, length = 16)
   private String phone;

   @Column(length = 120)
   private String name;

   @Column(name = "avatar_media_id")
   private Long avatarMediaId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 10)
   private UserRole role = UserRole.BUYER;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 2)
   private Lang lang = Lang.RU;

   @Column(name = "is_blocked", nullable = false)
   private boolean blocked;

   @Column(name = "onboarded_at")
   private Instant onboardedAt;

   @Column(name = "last_seen_at")
   private Instant lastSeenAt;

   @Column(name = "deletion_requested_at")
   private Instant deletionRequestedAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   protected User() {
   }

   public User(String phone, Lang lang) {
      this.phone = phone;
      this.lang = lang;
   }

   @PreUpdate
   void touch() {
      updatedAt = Instant.now();
   }

   /** Экран 03 пройден — роль выбрана хотя бы раз. */
   public boolean isOnboarded() {
      return onboardedAt != null;
   }

   /** Выбор режима; первый выбор завершает онбординг. */
   public void switchRole(UserRole role) {
      this.role = role;
      if (onboardedAt == null) {
         onboardedAt = Instant.now();
      }
   }

   /** Удаление подтверждено по SMS: через 30 дней данные сотрёт джоба. */
   public void requestDeletion() {
      deletionRequestedAt = Instant.now();
   }

   /** Вход в течение 30 дней после запроса на удаление отменяет его. */
   public boolean cancelDeletion() {
      if (deletionRequestedAt == null) {
         return false;
      }
      deletionRequestedAt = null;
      return true;
   }

   public Long getId() {
      return id;
   }

   public String getPhone() {
      return phone;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Long getAvatarMediaId() {
      return avatarMediaId;
   }

   public void setAvatarMediaId(Long avatarMediaId) {
      this.avatarMediaId = avatarMediaId;
   }

   public UserRole getRole() {
      return role;
   }

   public Lang getLang() {
      return lang;
   }

   public void setLang(Lang lang) {
      this.lang = lang;
   }

   public boolean isBlocked() {
      return blocked;
   }

   public void setBlocked(boolean blocked) {
      this.blocked = blocked;
   }

   public Instant getOnboardedAt() {
      return onboardedAt;
   }

   public Instant getLastSeenAt() {
      return lastSeenAt;
   }

   public Instant getDeletionRequestedAt() {
      return deletionRequestedAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getUpdatedAt() {
      return updatedAt;
   }
}
