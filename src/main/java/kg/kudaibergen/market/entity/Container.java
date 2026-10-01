package kg.kudaibergen.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Контейнер (бокс) — торговое место: ряд, сторона, номер. Один контейнер — один магазин. */
@Entity
@Table(name = "containers")
public class Container {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "row_id", nullable = false)
   private Long rowId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 5)
   private Side side;

   @Column(nullable = false)
   private short number;

   @Column(name = "pos_in_row", nullable = false)
   private short posInRow;

   @Column(name = "qr_token", nullable = false, unique = true, length = 32)
   private String qrToken;

   @Column(name = "tenant_phone", length = 16)
   private String tenantPhone;

   /** ФИО арендатора из базы рынка (импорт списка арендаторов). */
   @Column(name = "tenant_name", length = 120)
   private String tenantName;

   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   protected Container() {
   }

   public Container(Long rowId, Side side, short number, String qrToken) {
      this.rowId = rowId;
      this.side = side;
      this.number = number;
      this.posInRow = number;
      this.qrToken = qrToken;
   }

   public Long getId() {
      return id;
   }

   public Long getRowId() {
      return rowId;
   }

   public Side getSide() {
      return side;
   }

   public short getNumber() {
      return number;
   }

   public short getPosInRow() {
      return posInRow;
   }

   public String getQrToken() {
      return qrToken;
   }

   public String getTenantPhone() {
      return tenantPhone;
   }

   public void setTenantPhone(String tenantPhone) {
      this.tenantPhone = tenantPhone;
   }

   public String getTenantName() {
      return tenantName;
   }

   public void setTenantName(String tenantName) {
      this.tenantName = tenantName;
   }

   public boolean isActive() {
      return active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }
}
