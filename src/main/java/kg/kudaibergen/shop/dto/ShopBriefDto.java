package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/**
 * Магазин плоско — в карточке запчасти в сетке (27, 30): «Автодеталь Азамат · Ряд 14 · Бокс 12 · открыт».
 * row — код ряда («14», «Ю»), rowLabel — «Ряд 14» / «Жайма 3», container — номер бокса.
 */
public record ShopBriefDto(Long id, String name, @Nullable String avatarUrl, BigDecimal rating, String row, String rowLabel,
                           int container, Side side, boolean isOpenNow, LocalTime openTo) {

   public static ShopBriefDto of(ShopCardDto card) {
      return new ShopBriefDto(card.id(), card.name(), card.avatarUrl(), card.rating(), card.location().rowCode(),
            card.location().rowLabel(), card.location().number(), card.location().side(), card.open().openNow(),
            card.open().openTo());
   }
}
