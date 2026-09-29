package kg.kudaibergen.garage.dto;

import kg.kudaibergen.garage.entity.Brand;

/**
 * Марка для плиток и чипов. shortName — подпись в плитке («Mercedes»).
 * logoUrl = null — рисовать букву placeholder цветом color.
 */
public record BrandDto(Long id, String slug, String name, String shortName, String logoUrl, String placeholder,
                       String color, boolean popular) {

   public static BrandDto of(Brand brand) {
      return new BrandDto(brand.getId(), brand.getSlug(), brand.getName(), brand.getShortName(), brand.getLogoUrl(),
            brand.getPlaceholder(), brand.getColor(), brand.isPopular());
   }
}
