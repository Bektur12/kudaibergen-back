package kg.kudaibergen.garage.dto;

import kg.kudaibergen.garage.entity.Brand;

/** Марка для плиток и чипов. logoUrl = null — рисовать букву placeholder цветом color. */
public record BrandDto(Long id, String slug, String name, String logoUrl, String placeholder, String color,
                       boolean popular) {

   public static BrandDto of(Brand brand) {
      return new BrandDto(brand.getId(), brand.getSlug(), brand.getName(), brand.getLogoUrl(),
            brand.getPlaceholder(), brand.getColor(), brand.isPopular());
   }
}
