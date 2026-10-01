package kg.kudaibergen.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.user.entity.Lang;

/** Категория запчастей: Ходовая, Тормоза, Двигатель, Оптика… */
@Entity
@Table(name = "categories")
public class Category {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true, length = 40)
   private String slug;

   @Column(name = "name_ru", nullable = false, length = 60)
   private String nameRu;

   @Column(name = "name_kg", nullable = false, length = 60)
   private String nameKg;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   /** Скрыта в админке: нет в списках приложения. */
   @Column(name = "is_active", nullable = false)
   private boolean active = true;

   protected Category() {
   }

   public String name(Lang lang) {
      return lang == Lang.KG ? nameKg : nameRu;
   }

   public boolean isActive() {
      return active;
   }

   public Long getId() {
      return id;
   }

   public String getSlug() {
      return slug;
   }

   public String getNameRu() {
      return nameRu;
   }

   public String getNameKg() {
      return nameKg;
   }

   public short getSortOrder() {
      return sortOrder;
   }
}
