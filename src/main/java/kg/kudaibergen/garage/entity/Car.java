package kg.kudaibergen.garage.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Машина в гараже покупателя (экран 04). */
@Entity
@Table(name = "cars")
public class Car {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "user_id", nullable = false)
   private Long userId;

   @ManyToOne(fetch = FetchType.EAGER, optional = false)
   @JoinColumn(name = "brand_id", nullable = false)
   private Brand brand;

   @ManyToOne(fetch = FetchType.EAGER, optional = false)
   @JoinColumn(name = "model_id", nullable = false)
   private CarModel model;

   @Column(nullable = false)
   private short year;

   @Column(length = 40)
   private String engine;

   @Column(length = 20)
   private String vin;

   @Column(length = 40)
   private String name;

   @Column(name = "is_primary", nullable = false)
   private boolean primary;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   protected Car() {
   }

   public Car(Long userId, Brand brand, CarModel model, short year) {
      this.userId = userId;
      this.brand = brand;
      this.model = model;
      this.year = year;
   }

   @PreUpdate
   void touch() {
      updatedAt = Instant.now();
   }

   public void changeModel(Brand brand, CarModel model) {
      this.brand = brand;
      this.model = model;
   }

   public Long getId() {
      return id;
   }

   public Long getUserId() {
      return userId;
   }

   public Brand getBrand() {
      return brand;
   }

   public CarModel getModel() {
      return model;
   }

   public short getYear() {
      return year;
   }

   public void setYear(short year) {
      this.year = year;
   }

   public String getEngine() {
      return engine;
   }

   public void setEngine(String engine) {
      this.engine = engine;
   }

   public String getVin() {
      return vin;
   }

   public void setVin(String vin) {
      this.vin = vin;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isPrimary() {
      return primary;
   }

   public void setPrimary(boolean primary) {
      this.primary = primary;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
