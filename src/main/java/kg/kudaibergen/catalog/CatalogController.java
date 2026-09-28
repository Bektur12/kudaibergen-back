package kg.kudaibergen.catalog;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.catalog.dto.PartDetailDto;
import kg.kudaibergen.catalog.dto.PartSearchResultDto;
import kg.kudaibergen.catalog.dto.PartSort;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.request.entity.PartCondition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Каталог для покупателя и гостя (экраны 27–30). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Каталог")
public class CatalogController {

   private final CatalogService catalog;

   public CatalogController(CatalogService catalog) {
      this.catalog = catalog;
   }

   @GetMapping("/parts/search")
   @Operation(summary = "Поиск запчастей (27)", description = """
         q — название, производитель или номер детали (прощает опечатки, знает синонимы). Машина: carId из гаража
         (нужен вход) или brandId + modelId + year (28, modelId/year необязательны). По умолчанию только в наличии,
         сортировка «Дешевле». NEAREST — по расстоянию от точки x,y схемы (без неё — от главного входа).
         Сначала подходящие именно к модели, потом «ко всем моделям» марки.""")
   public PartSearchResultDto search(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long carId,
                                     @RequestParam(required = false) Long brandId,
                                     @RequestParam(required = false) Long modelId,
                                     @RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) PartCondition condition,
                                     @RequestParam(required = false) Integer priceMin,
                                     @RequestParam(required = false) Integer priceMax,
                                     @RequestParam(required = false) Boolean inStock,
                                     @Parameter(description = "Только открытые сейчас боксы")
                                     @RequestParam(required = false) Boolean openOnly,
                                     @RequestParam(required = false) PartSort sort,
                                     @RequestParam(required = false) Double x,
                                     @RequestParam(required = false) Double y,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit) {
      return catalog.search(userId(principal), new CatalogService.SearchParams(q, carId, brandId, modelId, year,
            categoryId, condition, priceMin, priceMax, inStock, openOnly, sort, x, y, null), cursor, limit);
   }

   @GetMapping("/parts/search/count")
   @Operation(summary = "Сколько найдётся («Показать 86» на 28)", description = "Те же фильтры, что /parts/search")
   public long count(@AuthenticationPrincipal AuthPrincipal principal,
                     @RequestParam(required = false) String q,
                     @RequestParam(required = false) Long carId,
                     @RequestParam(required = false) Long brandId,
                     @RequestParam(required = false) Long modelId,
                     @RequestParam(required = false) Integer year,
                     @RequestParam(required = false) Long categoryId,
                     @RequestParam(required = false) PartCondition condition,
                     @RequestParam(required = false) Integer priceMin,
                     @RequestParam(required = false) Integer priceMax,
                     @RequestParam(required = false) Boolean inStock,
                     @RequestParam(required = false) Boolean openOnly) {
      return catalog.count(userId(principal), new CatalogService.SearchParams(q, carId, brandId, modelId, year,
            categoryId, condition, priceMin, priceMax, inStock, openOnly, null, null, null, null));
   }

   @GetMapping("/shops/{shopId}/parts")
   @Operation(summary = "Запчасти магазина (вкладка «Запчасти» на 30)", description = "Фильтры — как у /parts/search")
   public PartSearchResultDto shopParts(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable Long shopId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Long carId,
                                        @RequestParam(required = false) Long brandId,
                                        @RequestParam(required = false) Long modelId,
                                        @RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) PartSort sort,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer limit) {
      return catalog.search(userId(principal), new CatalogService.SearchParams(q, carId, brandId, modelId, year,
            categoryId, null, null, null, false, null, sort, null, null, shopId), cursor, limit);
   }

   @GetMapping("/parts/{id}")
   @Operation(summary = "Карточка запчасти (29)", description = """
         Машина (carId или brandId + modelId + year) — для плашки «Подходит к вашей Camry 50 · 2012».
         Открытие покупателем считается просмотром (не чаще раза в час на человека).""")
   public PartDetailDto detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                               @RequestParam(required = false) Long carId,
                               @RequestParam(required = false) Long brandId,
                               @RequestParam(required = false) Long modelId,
                               @RequestParam(required = false) Integer year,
                               HttpServletRequest http,
                               @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                     required = false) String language) {
      Long userId = userId(principal);
      String viewer = userId != null ? "u" + userId : "ip" + http.getRemoteAddr();
      return catalog.detail(id, userId, viewer, carId, brandId, modelId, year, Langs.fromHeader(language));
   }

   @PutMapping("/parts/{id}/favorite")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "В избранное (сердечко)")
   public void favorite(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      catalog.addFavorite(principal.userId(), id);
   }

   @DeleteMapping("/parts/{id}/favorite")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Убрать из избранного")
   public void unfavorite(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      catalog.removeFavorite(principal.userId(), id);
   }

   @GetMapping("/me/favorite-parts")
   @Operation(summary = "Избранные запчасти (профиль)")
   public List<PartCardDto> favorites(@AuthenticationPrincipal AuthPrincipal principal) {
      return catalog.favorites(principal.userId());
   }

   private static Long userId(AuthPrincipal principal) {
      return principal == null ? null : principal.userId();
   }
}
