package kg.kudaibergen.user.entity;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicles")
public class Vehicle {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "user_id", nullable = false)
   private User user;

   @Column(nullable = false, length = 60)
   private String brand;

   @Column(nullable = false, length = 60)
   private String model;

   @Column(length = 60)
   private String generation;

   @Column(name = "year")
   private Short year;

   @Column(length = 40)
   private String engine;

   @Column(name = "body_type", length = 40)
   private String bodyType;

   @Column(length = 17)
   private String vin;

   @Column(name = "is_default", nullable = false)
   private boolean isDefault;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Vehicle() {
   }

   public Vehicle(User user) {
      this.user = user;
   }

   /** "Toyota Camry 2018, 2.5 бензин" — то, что уходит продавцу в карточке запроса. */
   public String describe() {
      String head = Stream.of(brand, model, generation, year == null ? null : String.valueOf(year))
            .filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
      return engine == null || engine.isBlank() ? head : head + ", " + engine;
   }

   public Long getId() {
      return id;
   }

   public User getUser() {
      return user;
   }

   public String getBrand() {
      return brand;
   }

   public void setBrand(String brand) {
      this.brand = brand;
   }

   public String getModel() {
      return model;
   }

   public void setModel(String model) {
      this.model = model;
   }

   public String getGeneration() {
      return generation;
   }

   public void setGeneration(String generation) {
      this.generation = generation;
   }

   public Short getYear() {
      return year;
   }

   public void setYear(Short year) {
      this.year = year;
   }

   public String getEngine() {
      return engine;
   }

   public void setEngine(String engine) {
      this.engine = engine;
   }

   public String getBodyType() {
      return bodyType;
   }

   public void setBodyType(String bodyType) {
      this.bodyType = bodyType;
   }

   public String getVin() {
      return vin;
   }

   public void setVin(String vin) {
      this.vin = vin;
   }

   public boolean isDefault() {
      return isDefault;
   }

   public void setDefault(boolean isDefault) {
      this.isDefault = isDefault;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
