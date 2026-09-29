package kg.kudaibergen.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Статистика бокса (17). */
@RestController
@RequestMapping("/api/v1/my/shop")
@Tag(name = "Мой бокс")
public class ShopStatsController {

   private final ShopStatsService stats;

   public ShopStatsController(ShopStatsService stats) {
      this.stats = stats;
   }

   @GetMapping("/stats")
   @Operation(summary = "Статистика бокса за неделю или месяц (17)", description = """
         Последние 7 (WEEK) или 30 (MONTH) дней: запросы по маркам, «Есть» / «Нет», написали в чат, подошли,
         продажи, без ответа (время вышло или закрыт, а бокс не ответил — «Смотреть» → filter=UNANSWERED),
         среднее время ответа, чаще всего спрашивали, просмотры запчастей.""")
   public ShopStatsDto stats(@AuthenticationPrincipal AuthPrincipal principal,
                             @RequestParam(defaultValue = "WEEK") StatsPeriod period,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return stats.forMember(principal.userId(), period, Langs.fromHeader(language));
   }
}
