package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.market.dto.LocationDto;

/**
 * Профиль продавца для покупателя (экран 30). phone — только если продавец разрешил показывать
 * (иначе «Позвонить» неактивна). Фото места и счётчики товаров/отзывов добавят модули media и catalog.
 */
public record ShopPublicDto(Long id, String name, String avatarUrl, BigDecimal rating, int reviewsCount,
                            LocationDto location, OpenStateDto open, List<BrandDto> brands,
                            List<CategoryDto> categories, String phone, boolean isFavorite) {
}
