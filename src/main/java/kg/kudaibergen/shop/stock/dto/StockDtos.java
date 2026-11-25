package kg.kudaibergen.shop.stock.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.shop.stock.StockSource;
import org.springframework.lang.Nullable;

/** «Всегда есть»: запросы и ответы кабинета продавца и поиска покупателя. */
public final class StockDtos {

   /** Сколько строк может быть у магазина: больше — уже «всё подряд», такой список ничего не говорит. */
   public static final int MAX_ITEMS = 150;

   private StockDtos() {
   }

   /** Деталь (GET /requests/hints) на марку и модель; без марки — на все марки магазина. */
   public record StockItemInput(@NotNull(message = "Выберите деталь") Long hintId, @Nullable Long brandId,
                                @Nullable Long modelId) {
   }

   public record SetStock(
         @NotNull @Size(max = MAX_ITEMS, message = "Не больше " + MAX_ITEMS + " позиций") List<@Valid StockItemInput> items) {
   }

   /** Одна строка; fromAnswers — принята подсказка из ответов «Есть». */
   public record AddStockItem(@NotNull(message = "Выберите деталь") Long hintId, @Nullable Long brandId,
                              @Nullable Long modelId, boolean fromAnswers) {
   }

   /** brandName / modelName null — «все марки» / «все модели». */
   public record StockItemDto(Long id, Long hintId, String text, @Nullable Long categoryId, @Nullable Long brandId,
                              @Nullable String brandName, @Nullable Long modelId, @Nullable String modelName,
                              StockSource source) {
   }

   /** «Вы часто отвечаете „Есть“ на фары Lexus — добавить в список?» */
   public record StockSuggestionDto(Long hintId, String text, @Nullable Long brandId, @Nullable String brandName,
                                    int answers) {
   }

   /**
    * «Обычно есть у 12 магазинов»: total — всего, shops — первые limit по порядку показа, containerIds — их
    * контейнеры на рынке (подсветка на карте и запрос «Контейнерам» — «Спросить у них»).
    */
   public record StockingShopsDto(int total, List<ShopCardDto> shops, List<Long> containerIds) {
   }
}
