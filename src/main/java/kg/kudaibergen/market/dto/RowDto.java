package kg.kudaibergen.market.dto;

import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.market.entity.RowType;

/** Ряд для сетки рядов (экран 10а) и поиска. onMap = false — ряда ещё нет на схеме (нет формы и маршрута). */
public record RowDto(Long id, String code, String label, RowType type, int containerCount, boolean onMap) {

   public static RowDto of(MarketSnapshot.RowView view) {
      return new RowDto(view.row().getId(), view.row().getCode(), view.row().getLabel(), view.row().getType(),
            view.all().size(), view.onMap());
   }
}
