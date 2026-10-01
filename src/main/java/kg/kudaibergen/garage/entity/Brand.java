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

/** Марка авто. Справочник ведёт суперадмин, сид — design/brands.json. */
@Entity
@Table(name = "brands")
public class Brand {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true, length = 40)
   private String slug;

   @Column(nullable = false, length = 60)
   private String name;

   /** Подпись в плитке (23, 28): «Mercedes» вместо «Mercedes-Benz». */
   @Column(name = "short_name", nullable = false, length = 30)
   private String shortName;

   @Column(name = "logo_url")
   private String logoUrl;

   @Column(nullable = false, length = 4)
   private String placeholder;

   @Column(nullable = false, length = 7)
   private String color;

   @Column(nullable = false)
   private boolean popular;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(nullable = false, columnDefinition = "text[]")
   private List<String> aliases = new ArrayList<>();

   /** Скрыта в админке: нет в списках приложения, но уже выбранная работает. */
   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   @Column(name = "logo_media_id")
   private Long logoMediaId;

   protected Brand() {
   }

   public Long getId() {
      return id;
   }

   public String getSlug() {
      return slug;
   }

   public String getName() {
      return name;
   }

   public String getShortName() {
      return shortName == null ? name : shortName;
   }

   /** Логотип: загруженный в админке отдаётся по стабильной ссылке, иначе — статичный файл из assets. */
   public String getLogoUrl() {
      if (logoMediaId != null) {
         return "/api/v1/brands/" + id + "/logo";
      }
      return logoUrl;
   }

   public String getPlaceholder() {
      return placeholder;
   }

   public String getColor() {
      return color;
   }

   public boolean isActive() {
      return active;
   }

   public Long getLogoMediaId() {
      return logoMediaId;
   }

   public boolean isPopular() {
      return popular;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public List<String> getAliases() {
      return aliases;
   }
}
