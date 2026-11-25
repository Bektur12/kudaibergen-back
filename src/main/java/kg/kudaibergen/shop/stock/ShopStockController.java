package kg.kudaibergen.shop.stock;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.request.dto.PartHintDto;
import kg.kudaibergen.shop.stock.dto.StockDtos.AddStockItem;
import kg.kudaibergen.shop.stock.dto.StockDtos.SetStock;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockItemDto;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockSuggestionDto;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockingShopsDto;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** «Что у меня всегда есть» в кабинете продавца и «Обычно есть у…» для покупателя. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Всегда в наличии")
public class ShopStockController {

   private final ShopStockService stock;

   public ShopStockController(ShopStockService stock) {
      this.stock = stock;
   }

   @GetMapping("/my/shop/stock")
   @Operation(summary = "Что у меня всегда есть", description = """
         Детали из справочника подсказок (GET /requests/hints) на марку и модель. brandId null — на все марки
         магазина, modelId null — на все модели марки.""")
   public List<StockItemDto> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                        required = false) String language) {
      return stock.mine(principal.userId(), Langs.fromHeader(language));
   }

   @PutMapping("/my/shop/stock")
   @Operation(summary = "Сохранить список целиком (владелец)", description = """
         До 150 строк, повторы убираются. Марка — только из «Марки, которые продаю» (400 BRAND_NOT_IN_SHOP),
         модель — только с маркой и этой марки (400 MODEL_WITHOUT_BRAND, UNKNOWN_MODEL); 400 UNKNOWN_PART_HINT.
         Пустой список — очистить.""")
   public List<StockItemDto> replace(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody SetStock request,
                                     @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                           required = false) String language) {
      return stock.replace(principal.userId(), request.items(), Langs.fromHeader(language));
   }

   @PostMapping("/my/shop/stock/items")
   @Operation(summary = "Добавить одну строку (владелец)",
         description = "«Добавить» у подсказки из ответов — fromAnswers = true. Уже есть — список не меняется")
   public List<StockItemDto> add(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody AddStockItem request,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                       required = false) String language) {
      return stock.add(principal.userId(), request, Langs.fromHeader(language));
   }

   @DeleteMapping("/my/shop/stock/items/{id}")
   @Operation(summary = "Убрать строку (владелец)")
   public List<StockItemDto> remove(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                    @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                          required = false) String language) {
      return stock.remove(principal.userId(), id, Langs.fromHeader(language));
   }

   @GetMapping("/my/shop/stock/suggestions")
   @Operation(summary = "Подсказки из ответов «Есть»",
         description = "На что магазин ответил «Есть» от 3 раз за 60 дней, а в списке этого нет. До 10 штук")
   public List<StockSuggestionDto> suggestions(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Parameter(hidden = true) @RequestHeader(
                                                     value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
                                               String language) {
      return stock.suggestions(principal.userId(), Langs.fromHeader(language));
   }

   @GetMapping("/shops/stock-hints")
   @Operation(summary = "Все детали справочника", description = """
         Для выбора «всегда есть» и чтобы узнать деталь в тексте покупателя. Самые частые сверху; id — hintId
         в /my/shop/stock, /shops/stocking и в запросе.""")
   public List<PartHintDto> hints(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
         required = false) String language) {
      return stock.allHints(Langs.fromHeader(language));
   }

   @GetMapping("/shops/stocking")
   @Operation(summary = "Обычно есть у… (покупатель)", description = """
         Действующие магазины, у которых деталь отмечена «всегда есть» — по заявлению продавца, это не остаток
         на складе. Порядок: точнее совпадение (модель → марка → все марки), быстрее отвечает, выше рейтинг;
         магазины с пометкой «ложное Есть» — в конце. total — сколько всего, shops — первые limit (1–50,
         по умолчанию 20), containerIds — для подсветки на карте и запроса «Контейнерам».""")
   public StockingShopsDto stocking(@RequestParam Long hintId,
                                    @RequestParam(required = false) Long brandId,
                                    @RequestParam(required = false) Long modelId,
                                    @Parameter(description = "Рынок (GET /market/markets); 0 — вне рынков")
                                    @RequestParam(required = false) Long marketId,
                                    @RequestParam(required = false) Integer limit) {
      return stock.stocking(hintId, brandId, modelId, marketId, limit);
   }
}
