package kg.kudaibergen.market.dto;

import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.market.entity.Side;

/** «Ряд 14 · Бокс 12 · северная» — место магазина на рынке. */
public record LocationDto(Long rowId, String rowCode, String rowLabel, Long containerId, int number, Side side) {

   public static LocationDto of(MarketSnapshot.ContainerView view) {
      return new LocationDto(view.row().row().getId(), view.row().row().getCode(), view.row().row().getLabel(),
            view.container().getId(), view.container().getNumber(), view.container().getSide());
   }
}
