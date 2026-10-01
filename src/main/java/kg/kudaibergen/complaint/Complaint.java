package kg.kudaibergen.complaint;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Жалоба для модерации админом рынка. target — id контейнера, магазина, товара, фото или отзыва. */
@Entity
@Table(name = "complaints")
public class Complaint {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "author_id")
   private Long authorId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 20)
   private ComplaintType type;

   @Column(name = "target_id", nullable = false)
   private Long targetId;

   @Column(length = 1000)
   private String text;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private ComplaintStatus status = ComplaintStatus.OPEN;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 23)
   private ComplaintReason reason = ComplaintReason.OTHER;

   /** Режим, в котором был заявитель: BUYER / SELLER / MASTER. */
   @Column(name = "reporter_role", length = 6)
   private String reporterRole;

   @Column(name = "related_request_id")
   private Long relatedRequestId;

   @Column(name = "related_service_request_id")
   private Long relatedServiceRequestId;

   @Enumerated(EnumType.STRING)
   @Column(length = 9)
   private ComplaintOutcome outcome;

   /** Комментарий администрации — «для журнала и для продавца». */
   @Column(length = 1000)
   private String resolution;

   @Column(name = "resolved_by")
   private Long resolvedBy;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "resolved_at")
   private Instant resolvedAt;

   protected Complaint() {
   }

   public Complaint(Long authorId, ComplaintType type, Long targetId, String text) {
      this.authorId = authorId;
      this.type = type;
      this.targetId = targetId;
      this.text = text;
   }

   public Complaint(Long authorId, ComplaintType type, Long targetId, String text, ComplaintReason reason,
                    String reporterRole, Long relatedRequestId, Long relatedServiceRequestId) {
      this(authorId, type, targetId, text);
      this.reason = reason == null ? ComplaintReason.OTHER : reason;
      this.reporterRole = reporterRole;
      this.relatedRequestId = relatedRequestId;
      this.relatedServiceRequestId = relatedServiceRequestId;
   }

   public void resolve(ComplaintStatus status, ComplaintOutcome outcome, String resolution, Long resolvedBy) {
      this.outcome = outcome;
      resolve(status, resolution, resolvedBy);
   }

   public void resolve(ComplaintStatus status, String resolution, Long resolvedBy) {
      this.status = status;
      this.resolution = resolution;
      this.resolvedBy = resolvedBy;
      this.resolvedAt = Instant.now();
   }

   public ComplaintReason getReason() {
      return reason;
   }

   public String getReporterRole() {
      return reporterRole;
   }

   public Long getRelatedRequestId() {
      return relatedRequestId;
   }

   public Long getRelatedServiceRequestId() {
      return relatedServiceRequestId;
   }

   public ComplaintOutcome getOutcome() {
      return outcome;
   }

   public Long getResolvedBy() {
      return resolvedBy;
   }

   public Long getId() {
      return id;
   }

   public Long getAuthorId() {
      return authorId;
   }

   public ComplaintType getType() {
      return type;
   }

   public Long getTargetId() {
      return targetId;
   }

   public String getText() {
      return text;
   }

   public ComplaintStatus getStatus() {
      return status;
   }

   public String getResolution() {
      return resolution;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getResolvedAt() {
      return resolvedAt;
   }
}
