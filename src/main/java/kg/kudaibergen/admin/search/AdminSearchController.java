package kg.kudaibergen.admin.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/search")
@Tag(name = "Админка: поиск")
public class AdminSearchController {

   private final AdminSearchService search;

   public AdminSearchController(AdminSearchService search) {
      this.search = search;
   }

   @GetMapping
   @PreAuthorize("hasAnyAuthority('USERS_VIEW', 'SELLERS_VIEW', 'MASTERS_VIEW', 'MARKET_VIEW')")
   @Operation(summary = "Поиск в шапке: телефон, магазин, мастер, контейнер",
         description = "Группы по 5. Контейнер — «14 12», «Ряд 14 · 12». Запрос короче 2 символов — пустые группы")
   public AdminSearchDto search(@RequestParam(defaultValue = "") String q,
                                @AuthenticationPrincipal AuthPrincipal principal) {
      return search.search(q, principal);
   }
}
