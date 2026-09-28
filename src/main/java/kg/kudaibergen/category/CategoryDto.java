package kg.kudaibergen.category;

import kg.kudaibergen.user.entity.Lang;

/** Категория на языке запроса. */
public record CategoryDto(Long id, String slug, String name) {

   public static CategoryDto of(Category category, Lang lang) {
      return new CategoryDto(category.getId(), category.getSlug(), category.name(lang));
   }
}
