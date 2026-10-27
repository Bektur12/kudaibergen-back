package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/**
 * Магазин плоско — в карточке запчасти в сетке (27, 30): «Автодеталь Азамат · Ряд 14 · Бокс 12 · открыт».
 * row — код ряда («14», «Ю»), rowLabel — «Ряд 14» / «Жайма 3», container — номер бокса. Вне рынка
 * (onMarket = false) row, rowLabel, container, side — null, вместо них address.
 */
public record ShopBriefDto(Long id, String name, @Nullable String avatarUrl, BigDecimal rating, @Nullable String row,
                           @Nullable String rowLabel, @Nullable Integer container, @Nullable Side side, boolean isOpenNow,
                           LocalTime openTo, boolean onMarket, @Nullable String address) {

   public static ShopBriefDto of(ShopCardDto card) {
      LocationDto location = card.location();
      return new ShopBriefDto(card.id(), card.name(), card.avatarUrl(), card.rating(),
            location == null ? null : location.rowCode(), location == null ? null : location.rowLabel(),
            location == null ? null : (int) location.number(), location == null ? null : location.side(),
            card.open().openNow(), card.open().openTo(), card.onMarket(), card.address());
   }
}
