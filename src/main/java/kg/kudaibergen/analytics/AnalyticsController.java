package kg.kudaibergen.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.analytics.dto.AnalyticsPeriod;
import kg.kudaibergen.analytics.dto.SellerAnalyticsResponse;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.store.StoreService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/my-store/analytics")
@Tag(name = "Аналитика продавца")
public class AnalyticsController {

   private final AnalyticsService analyticsService;
   private final StoreService storeService;

   public AnalyticsController(AnalyticsService analyticsService, StoreService storeService) {
      this.analyticsService = analyticsService;
      this.storeService = storeService;
   }

   @GetMapping
   @Operation(summary = "Статистика магазина за период")
   public SellerAnalyticsResponse analytics(@AuthenticationPrincipal AuthPrincipal principal,
                                            @RequestParam(defaultValue = "WEEK") AnalyticsPeriod period) {
      return analyticsService.forStore(storeService.requireOwnStoreId(principal.userId()), period);
   }
}
