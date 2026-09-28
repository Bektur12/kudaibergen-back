package kg.kudaibergen.catalog;

/** События каталога для пушей тем, у кого запчасть в избранном (ТЗ 5.5). */
public final class PartEvents {

   private PartEvents() {
   }

   /** Цена опубликованной запчасти снизилась. */
   public record PriceDropped(Long partId, int oldPrice, int newPrice) {
   }

   /** Опубликованная запчасть закончилась (количество стало 0). */
   public record OutOfStock(Long partId) {
   }
}
