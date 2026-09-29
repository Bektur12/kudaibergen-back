package kg.kudaibergen.market.dto;

import org.springframework.lang.Nullable;

/** QR-табличка ряда или наклейка контейнера: точка «я здесь» встаёт точно на место. */
public record QrResolveDto(Type type, @Nullable RowDto row, @Nullable LocationDto container, double x, double y) {

   public enum Type {
      ROW, CONTAINER
   }
}
