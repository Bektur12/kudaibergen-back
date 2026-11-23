package kg.kudaibergen.market.dto;

import kg.kudaibergen.market.entity.Bazaar;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.lang.Nullable;

/**
 * Рынок в выборе «Где вы торгуете» и в фильтре покупателя. hasScheme — есть схема рядов (Кудайберген): там
 * выбирают контейнер; без схемы — место текстом. hasMap — есть карта рынка (GET /market/markets/{id}/map):
 * у Кудайбергена с контейнерами, у остальных — ряды, проходы и входы для ориентира.
 * lat/lng — точка рынка («Как пройти»), null — ещё не задана.
 */
public record MarketDto(Long id, String code, String name, boolean hasScheme, boolean hasMap,
                        @Nullable Double lat, @Nullable Double lng) {

   public static MarketDto of(Bazaar market, Lang lang) {
      return new MarketDto(market.getId(), market.getCode(), market.name(lang), market.hasScheme(), market.hasMap(),
            market.getLat(), market.getLng());
   }
}
