package kg.kudaibergen.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.request.dto.SellerRequestFilter;
import kg.kudaibergen.request.dto.SellerRequestRow;
import kg.kudaibergen.store.StoreService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Лента запросов продавца. Группировку по категориям делает клиент. */
@RestController
@RequestMapping("/api/v1/my-store/requests")
@Validated
@Tag(name = "Запросы (продавец)")
public class MyStoreRequestController {

   private final RequestService requestService;
   private final StoreService storeService;

   public MyStoreRequestController(RequestService requestService, StoreService storeService) {
      this.requestService = requestService;
      this.storeService = storeService;
   }

   @GetMapping
   @Operation(summary = "Запросы, пришедшие моему магазину")
   public PageResponse<SellerRequestRow> list(@AuthenticationPrincipal AuthPrincipal principal,
                                              @RequestParam(defaultValue = "ALL") SellerRequestFilter filter,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      Long storeId = storeService.requireOwnStoreId(principal.userId());
      return requestService.forStore(storeId, filter, page, size);
   }

   @PostMapping("/{id}/seen")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Отметить запрос просмотренным")
   public void markSeen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      requestService.markSeen(id, storeService.requireOwnStoreId(principal.userId()));
   }
}
