package kg.kudaibergen.market.dto;

import org.springframework.lang.Nullable;

/**
 * QR-табличка ряда или наклейка контейнера: точка «я здесь» встаёт точно на место.
 * x, y = null — ряда нет на схеме (geometry не задана), точку не ставим.
 */
public record QrResolveDto(Type type, @Nullable RowDto row, @Nullable LocationDto container, @Nullable Double x,
                           @Nullable Double y) {

   public enum Type {
      ROW, CONTAINER
   }
}
