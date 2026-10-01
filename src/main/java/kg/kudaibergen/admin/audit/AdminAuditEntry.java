package kg.kudaibergen.admin.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Строка журнала админки. before/after — JSON объекта, как его видел сотрудник. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntry {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "admin_id")
   private Long adminId;

   @Column(nullable = false, length = 40)
   private String action;

   @Column(name = "entity_type", nullable = false, length = 24)
   private String entityType;

   @Column(name = "entity_id")
   private Long entityId;

   @JdbcTypeCode(SqlTypes.JSON)
   private String before;

   @JdbcTypeCode(SqlTypes.JSON)
   private String after;

   @Column(length = 1000)
   private String comment;

   @Column(length = 45)
   private String ip;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected AdminAuditEntry() {
   }

   public AdminAuditEntry(Long adminId, String action, String entityType, Long entityId, String before, String after,
                          String comment, String ip) {
      this.adminId = adminId;
      this.action = action;
      this.entityType = entityType;
      this.entityId = entityId;
      this.before = before;
      this.after = after;
      this.comment = comment;
      this.ip = ip;
   }

   public Long getId() {
      return id;
   }

   public Long getAdminId() {
      return adminId;
   }

   public String getAction() {
      return action;
   }

   public String getEntityType() {
      return entityType;
   }

   public Long getEntityId() {
      return entityId;
   }

   public String getBefore() {
      return before;
   }

   public String getAfter() {
      return after;
   }

   public String getComment() {
      return comment;
   }

   public String getIp() {
      return ip;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
