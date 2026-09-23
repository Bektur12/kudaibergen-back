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

@Entity
@Table(name = "users")
public class User {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true, length = 20)
   private String phone;

   @Column(length = 120)
   private String name;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 40)
   private UserRole role;

   @Column(nullable = false, length = 80)
   private String city = "Бишкек";

   @Column(name = "is_blocked", nullable = false)
   private boolean blocked;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "last_seen_at")
   private Instant lastSeenAt;

   protected User() {
   }

   public User(String phone, UserRole role) {
      this.phone = phone;
      this.role = role;
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

   public UserRole getRole() {
      return role;
   }

   public void setRole(UserRole role) {
      this.role = role;
   }

   public String getCity() {
      return city;
   }

   public void setCity(String city) {
      this.city = city;
   }

   public boolean isBlocked() {
      return blocked;
   }

   public void setBlocked(boolean blocked) {
      this.blocked = blocked;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getLastSeenAt() {
      return lastSeenAt;
   }

   public void setLastSeenAt(Instant lastSeenAt) {
      this.lastSeenAt = lastSeenAt;
   }
}
