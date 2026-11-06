package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.PhotoDto;
import org.springframework.lang.Nullable;

/**
 * Профиль продавца для покупателя (экран 30). phone — только если продавец разрешил показывать
 * (иначе «Позвонить» неактивна). photos — фото места, первое — обложка («1 / 5»). counts — вкладки
 * «Запчасти 38», «Фото 5», «Отзывы 126». publicId — для ссылки «Поделиться» (GET /shops/public/{publicId}).
 * onMarket / address / lat / lng — как в ShopCardDto: вне рынка location = null, есть адрес и точка.
 * partCategories — фильтр вкладки «Запчасти»: категории, где у магазина есть запчасти, с их числом.
 */
public record ShopPublicDto(Long id, String publicId, String name, @Nullable String avatarUrl, BigDecimal rating, int reviewsCount,
                            @Nullable LocationDto location, OpenStateDto open, List<BrandDto> brands,
                            List<CategoryDto> categories, @Nullable String phone, boolean isFavorite, List<PhotoDto> photos,
                            ShopCounts counts, boolean onMarket, @Nullable String address, @Nullable Double lat,
                            @Nullable Double lng, List<ShopPartCategory> partCategories) {

   public record ShopCounts(long parts, int photos, int reviews) {
   }

   public record ShopPartCategory(Long id, String slug, String name, long count) {
   }
}
