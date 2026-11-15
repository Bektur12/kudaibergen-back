package kg.kudaibergen.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.user.entity.Lang;

/**
 * Рынок: Кудайберген — со схемой рядов и контейнеров; Дордой, Дордой Моторс, Геология — без схемы, место продавца
 * текстом. Справочник правится миграциями.
 */
@Entity
@Table(name = "markets")
public class Bazaar {

   /** Рынок со схемой — магазины в контейнерах и старые «место текстом» стоят здесь. */
   public static final long KUDAIBERGEN_ID = 1L;

   @Id
   private Long id;

   @Column(nullable = false, unique = true, length = 30)
   private String code;

   @Column(name = "name_ru", nullable = false, length = 60)
   private String nameRu;

   @Column(name = "name_kg", nullable = false, length = 60)
   private String nameKg;

   private Double lat;

   private Double lng;

   @Column(name = "has_scheme", nullable = false)
   private boolean hasScheme;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @Column(nullable = false)
   private boolean active = true;

   protected Bazaar() {
   }

   public Bazaar(Long id, String code, String nameRu, String nameKg, Double lat, Double lng, boolean hasScheme) {
      this.id = id;
      this.code = code;
      this.nameRu = nameRu;
      this.nameKg = nameKg;
      this.lat = lat;
      this.lng = lng;
      this.hasScheme = hasScheme;
   }

   public Long getId() {
      return id;
   }

   public String getCode() {
      return code;
   }

   public String getNameRu() {
      return nameRu;
   }

   public String name(Lang lang) {
      return lang == Lang.KG ? nameKg : nameRu;
   }

   public Double getLat() {
      return lat;
   }

   public Double getLng() {
      return lng;
   }

   public boolean hasScheme() {
      return hasScheme;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public boolean isActive() {
      return active;
   }
}
