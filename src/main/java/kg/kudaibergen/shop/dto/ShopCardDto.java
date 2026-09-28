package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;

import kg.kudaibergen.market.dto.LocationDto;

/** Краткая карточка магазина: ответы «Есть» (07), карточка запчасти (29), список рядом (15). */
public record ShopCardDto(Long id, String name, String avatarUrl, BigDecimal rating, int reviewsCount,
                          LocationDto location, OpenStateDto open) {
}
