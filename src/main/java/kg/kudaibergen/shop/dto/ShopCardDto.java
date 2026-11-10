package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;

import kg.kudaibergen.market.dto.LocationDto;
import org.springframework.lang.Nullable;

/**
 * Краткая карточка магазина: ответы «Есть» (05), карточка запчасти (29), список рядом (15).
 * phone — для «Позвонить» (null — продавец скрыл номер). onMarket — бокс на рынке Кудайберген (location — ряд и
 * контейнер); вне рынка (свой магазин, центр) location = null, address, lat, lng — адрес и точка. Место на рынке,
 * написанное продавцом («Ряд К, конт. 5»): onMarket = true, location = null, address — это место, lat/lng = null.
 */
public record ShopCardDto(Long id, String name, @Nullable String avatarUrl, BigDecimal rating, int reviewsCount,
                          @Nullable LocationDto location, OpenStateDto open, @Nullable String phone, boolean onMarket,
                          @Nullable String address, @Nullable Double lat, @Nullable Double lng) {
}
