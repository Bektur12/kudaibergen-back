package kg.kudaibergen.store;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.store.dto.StoreDetailsResponse;
import kg.kudaibergen.store.dto.StoreSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@Validated
@Tag(name = "Магазины (покупатель)")
public class StoreController {

   private final StoreService storeService;

   public StoreController(StoreService storeService) {
      this.storeService = storeService;
   }

   @GetMapping
   @Operation(summary = "Список магазинов с фильтром по категории и городу")
   public PageResponse<StoreSummaryResponse> list(@RequestParam(required = false) PartCategory category,
                                                  @RequestParam(required = false) String city,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      return storeService.search(category, city, page, size);
   }

   @GetMapping("/{id}")
   @Operation(summary = "Профиль магазина: филиалы, категории, рейтинг")
   public StoreDetailsResponse details(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return storeService.details(id, principal.userId());
   }
}
