package kg.kudaibergen.store.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "store_branches")
public class StoreBranch {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "store_id", nullable = false)
   private Store store;

   /** Только для чтения: id магазина без разворачивания ленивого прокси. */
   @Column(name = "store_id", insertable = false, updatable = false)
   private Long storeId;

   @Column(nullable = false, length = 200)
   private String address;

   @Column(nullable = false, length = 80)
   private String city;

   @Column(length = 20)
   private String phone;

   @Column(precision = 9, scale = 6)
   private BigDecimal latitude;

   @Column(precision = 9, scale = 6)
   private BigDecimal longitude;

   /** JSON вида {"open":"09:00","close":"19:00","days":["Пн","Вт"]}. */
   @JdbcTypeCode(SqlTypes.JSON)
   @Column(name = "work_hours")
   private String workHours;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected StoreBranch() {
   }

   public StoreBranch(Store store) {
      this.store = store;
   }

   public Long getId() {
      return id;
   }

   public Store getStore() {
      return store;
   }

   public Long getStoreId() {
      return storeId;
   }

   public String getAddress() {
      return address;
   }

   public void setAddress(String address) {
      this.address = address;
   }

   public String getCity() {
      return city;
   }

   public void setCity(String city) {
      this.city = city;
   }

   public String getPhone() {
      return phone;
   }

   public void setPhone(String phone) {
      this.phone = phone;
   }

   public BigDecimal getLatitude() {
      return latitude;
   }

   public void setLatitude(BigDecimal latitude) {
      this.latitude = latitude;
   }

   public BigDecimal getLongitude() {
      return longitude;
   }

   public void setLongitude(BigDecimal longitude) {
      this.longitude = longitude;
   }

   public String getWorkHours() {
      return workHours;
   }

   public void setWorkHours(String workHours) {
      this.workHours = workHours;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
