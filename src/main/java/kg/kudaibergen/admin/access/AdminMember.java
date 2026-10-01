package kg.kudaibergen.admin.access;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.common.security.AdminRole;

/** Сотрудник админки. Ключ — пользователь приложения: вход по тому же номеру, но отдельной сессией. */
@Entity
@Table(name = "admin_members")
public class AdminMember {

   @Id
   @Column(name = "user_id")
   private Long userId;

   @Enumerated(EnumType.STRING)
   @Column(name = "admin_role", nullable = false, length = 12)
   private AdminRole role;

   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   @Column(name = "full_name", nullable = false, length = 120)
   private String fullName;

   @Column(nullable = false, length = 80)
   private String title;

   @Column(name = "password_hash", length = 72)
   private String passwordHash;

   @Column(name = "password_changed_at")
   private Instant passwordChangedAt;

   @Column(name = "created_by")
   private Long createdBy;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "last_login_at")
   private Instant lastLoginAt;

   protected AdminMember() {
   }

   public AdminMember(Long userId, AdminRole role, String fullName, String title, Long createdBy) {
      this.userId = userId;
      this.role = role;
      this.fullName = fullName;
      this.title = title;
      this.createdBy = createdBy;
   }

   public void changePassword(String hash) {
      this.passwordHash = hash;
      this.passwordChangedAt = Instant.now();
   }

   public void loggedIn() {
      this.lastLoginAt = Instant.now();
   }

   public boolean hasPassword() {
      return passwordHash != null;
   }

   public Long getUserId() {
      return userId;
   }

   public AdminRole getRole() {
      return role;
   }

   public void setRole(AdminRole role) {
      this.role = role;
   }

   public boolean isActive() {
      return active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }

   public String getFullName() {
      return fullName;
   }

   public void setFullName(String fullName) {
      this.fullName = fullName;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getPasswordHash() {
      return passwordHash;
   }

   public Instant getPasswordChangedAt() {
      return passwordChangedAt;
   }

   public Long getCreatedBy() {
      return createdBy;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getLastLoginAt() {
      return lastLoginAt;
   }
}
