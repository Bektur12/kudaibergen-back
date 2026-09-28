package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.PhotoDto;

/**
 * Профиль продавца для покупателя (экран 30). phone — только если продавец разрешил показывать
 * (иначе «Позвонить» неактивна). photos — фото места, первое — обложка («1 / 5»). counts — вкладки
 * «Запчасти 38», «Фото 5», «Отзывы 126».
 */
public record ShopPublicDto(Long id, String name, String avatarUrl, BigDecimal rating, int reviewsCount,
                            LocationDto location, OpenStateDto open, List<BrandDto> brands,
                            List<CategoryDto> categories, String phone, boolean isFavorite, List<PhotoDto> photos,
                            Counts counts) {

   public record Counts(long parts, int photos, int reviews) {
   }
}
