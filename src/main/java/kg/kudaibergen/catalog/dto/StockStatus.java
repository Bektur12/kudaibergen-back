package kg.kudaibergen.catalog.dto;

/** Бейдж «В наличии» / «Нет» (24, 27, 29). Правило «quantity = 0 — нет» знает только сервер. */
public enum StockStatus {
   IN_STOCK,
   OUT_OF_STOCK;

   public static StockStatus of(boolean inStock) {
      return inStock ? IN_STOCK : OUT_OF_STOCK;
   }
}
