package kg.kudaibergen.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Ряд рынка: «Ряд 14», «Ряд Ю», «Жайма 3». Живёт между версиями схемы, ключ — code. */
@Entity
@Table(name = "market_rows")
public class MarketRow {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true, length = 20)
   private String code;

   @Column(nullable = false, length = 40)
   private String label;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 5)
   private RowType type;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String geometry;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @Column(name = "qr_token", nullable = false, unique = true, length = 32)
   private String qrToken;

   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   protected MarketRow() {
   }

   public MarketRow(String code, String label, RowType type, String geometry, short sortOrder, String qrToken) {
      this.code = code;
      this.label = label;
      this.type = type;
      this.geometry = geometry;
      this.sortOrder = sortOrder;
      this.qrToken = qrToken;
   }

   public void update(String label, RowType type, String geometry, short sortOrder) {
      this.label = label;
      this.type = type;
      this.geometry = geometry;
      this.sortOrder = sortOrder;
      this.active = true;
   }

   public Long getId() {
      return id;
   }

   public String getCode() {
      return code;
   }

   public String getLabel() {
      return label;
   }

   public RowType getType() {
      return type;
   }

   public String getGeometry() {
      return geometry;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public String getQrToken() {
      return qrToken;
   }

   public boolean isActive() {
      return active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }
}
