package kg.kudaibergen.garage.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Модель с поколением: «Camry 50», «E-класс W211». Годы выпуска — для проверки года машины. */
@Entity
@Table(name = "models")
public class CarModel {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "brand_id", nullable = false)
   private Long brandId;

   @Column(nullable = false, length = 60)
   private String name;

   @Column(length = 30)
   private String generation;

   @Column(name = "year_from")
   private Short yearFrom;

   @Column(name = "year_to")
   private Short yearTo;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(nullable = false, columnDefinition = "text[]")
   private List<String> aliases = new ArrayList<>();

   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   /** Подпись из админки вместо «модель + поколение». */
   @Column(name = "display_name", length = 80)
   private String displayName;

   protected CarModel() {
   }

   /** «Camry 50», «E-класс W211», «Sprinter»; подпись из админки, если задана. */
   public String label() {
      if (displayName != null && !displayName.isBlank()) {
         return displayName;
      }
      return generation == null ? name : name + " " + generation;
   }

   /** Год входит в годы выпуска (открытые границы не ограничивают). */
   public boolean covers(int year) {
      return (yearFrom == null || year >= yearFrom) && (yearTo == null || year <= yearTo);
   }

   public boolean isActive() {
      return active;
   }

   public String getDisplayName() {
      return displayName;
   }

   public Long getId() {
      return id;
   }

   public Long getBrandId() {
      return brandId;
   }

   public String getName() {
      return name;
   }

   public String getGeneration() {
      return generation;
   }

   public Short getYearFrom() {
      return yearFrom;
   }

   public Short getYearTo() {
      return yearTo;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public List<String> getAliases() {
      return aliases;
   }
}
