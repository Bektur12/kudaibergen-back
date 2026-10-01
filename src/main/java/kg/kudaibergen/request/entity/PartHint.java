package kg.kudaibergen.request.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.user.entity.Lang;

/** Подсказка «что нужно» на экране 06: «+ Колодки», «+ Радиатор». Справочник — сид V11. */
@Entity
@Table(name = "part_hints")
public class PartHint {

   @Id
   private Long id;

   @Column(name = "text_ru", nullable = false, length = 40)
   private String textRu;

   @Column(name = "text_kg", nullable = false, length = 40)
   private String textKg;

   @Column(name = "category_id")
   private Long categoryId;

   @Column(nullable = false)
   private int popularity;

   protected PartHint() {
   }

   public String text(Lang lang) {
      return lang == Lang.KG ? textKg : textRu;
   }

   public Long getId() {
      return id;
   }

   public Long getCategoryId() {
      return categoryId;
   }

   public int getPopularity() {
      return popularity;
   }
}
