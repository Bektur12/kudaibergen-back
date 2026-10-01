package kg.kudaibergen.admin.tenants;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Загруженный список арендаторов: предпросмотр (PREVIEW), после применения — APPLIED. */
@Entity
@Table(name = "tenant_imports")
public class TenantImport {

   public static final String PREVIEW = "PREVIEW";
   public static final String APPLIED = "APPLIED";

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "file_name", nullable = false, length = 200)
   private String fileName;

   @Column(name = "uploaded_by")
   private Long uploadedBy;

   @Column(nullable = false, length = 7)
   private String status = PREVIEW;

   @Column(name = "rows_total", nullable = false)
   private int rowsTotal;

   @Column(name = "rows_ok", nullable = false)
   private int rowsOk;

   @Column(name = "rows_error", nullable = false)
   private int rowsError;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String rows;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String errors;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "applied_at")
   private Instant appliedAt;

   protected TenantImport() {
   }

   public TenantImport(String fileName, Long uploadedBy, int rowsTotal, int rowsOk, int rowsError, String rows,
                       String errors) {
      this.fileName = fileName;
      this.uploadedBy = uploadedBy;
      this.rowsTotal = rowsTotal;
      this.rowsOk = rowsOk;
      this.rowsError = rowsError;
      this.rows = rows;
      this.errors = errors;
   }

   public void applied() {
      status = APPLIED;
      appliedAt = Instant.now();
   }

   public boolean isApplied() {
      return APPLIED.equals(status);
   }

   public Long getId() {
      return id;
   }

   public String getFileName() {
      return fileName;
   }

   public Long getUploadedBy() {
      return uploadedBy;
   }

   public String getStatus() {
      return status;
   }

   public int getRowsTotal() {
      return rowsTotal;
   }

   public int getRowsOk() {
      return rowsOk;
   }

   public int getRowsError() {
      return rowsError;
   }

   public String getRows() {
      return rows;
   }

   public String getErrors() {
      return errors;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getAppliedAt() {
      return appliedAt;
   }
}
