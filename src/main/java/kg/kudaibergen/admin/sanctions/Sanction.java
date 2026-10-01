package kg.kudaibergen.admin.sanctions;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Предупреждение или блокировка от администрации — история для карточек и счётчиков модерации. */
@Entity
@Table(name = "sanctions")
public class Sanction {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Enumerated(EnumType.STRING)
   @Column(name = "target_type", nullable = false, length = 6)
   private SanctionTarget targetType;

   @Column(name = "target_id", nullable = false)
   private Long targetId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 7)
   private SanctionType type;

   @Column(length = 1000)
   private String reason;

   @Column(name = "admin_id")
   private Long adminId;

   @Column(name = "active_until")
   private Instant activeUntil;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Sanction() {
   }

   public Sanction(SanctionTarget targetType, Long targetId, SanctionType type, String reason, Long adminId,
                   Instant activeUntil) {
      this.targetType = targetType;
      this.targetId = targetId;
      this.type = type;
      this.reason = reason;
      this.adminId = adminId;
      this.activeUntil = activeUntil;
   }

   public Long getId() {
      return id;
   }

   public SanctionTarget getTargetType() {
      return targetType;
   }

   public Long getTargetId() {
      return targetId;
   }

   public SanctionType getType() {
      return type;
   }

   public String getReason() {
      return reason;
   }

   public Long getAdminId() {
      return adminId;
   }

   public Instant getActiveUntil() {
      return activeUntil;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
