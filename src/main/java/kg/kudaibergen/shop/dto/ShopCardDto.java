package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;

import kg.kudaibergen.market.dto.LocationDto;
import org.springframework.lang.Nullable;

/**
 * Краткая карточка магазина: ответы «Есть» (05), карточка запчасти (29), список рядом (15).
 * phone — для «Позвонить» (null — продавец скрыл номер).
 */
public record ShopCardDto(Long id, String name, @Nullable String avatarUrl, BigDecimal rating, int reviewsCount,
                          LocationDto location, OpenStateDto open, @Nullable String phone) {
}
