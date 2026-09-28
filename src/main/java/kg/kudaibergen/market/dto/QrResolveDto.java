package kg.kudaibergen.market.dto;

/** QR-табличка ряда или наклейка контейнера: точка «я здесь» встаёт точно на место. */
public record QrResolveDto(Type type, RowDto row, LocationDto container, double x, double y) {

   public enum Type {
      ROW, CONTAINER
   }
}
