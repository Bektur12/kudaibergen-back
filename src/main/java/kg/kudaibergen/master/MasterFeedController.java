package kg.kudaibergen.master;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.master.dto.MasterFeedItemDto;
import kg.kudaibergen.master.dto.MasterStatsDto;
import kg.kudaibergen.master.dto.ServiceInputs;
import kg.kudaibergen.master.dto.ServiceOfferDto;
import kg.kudaibergen.stats.StatsPeriod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Заявки мастера (39) и его статистика. */
@RestController
@RequestMapping("/api/v1/my/master")
@Tag(name = "Заявки на услуги: мастер")
public class MasterFeedController {

   private final MasterFeedService feed;
   private final MasterStatsService stats;

   public MasterFeedController(MasterFeedService feed, MasterStatsService stats) {
      this.feed = feed;
      this.stats = stats;
   }

   @GetMapping("/requests")
   @Operation(summary = "Заявки мастера (39)", description = """
         NEW — активные без ответа (таймер по expiresAt, «1,2 км от вас»), ANSWERED — «Могу помочь»,
         EXPIRED — время вышло без ответа.""")
   public CursorPage<MasterFeedItemDto> feed(@AuthenticationPrincipal AuthPrincipal principal,
                                             @RequestParam(defaultValue = "NEW") MasterFeedService.MasterFeedFilter filter,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit,
                                             @Parameter(hidden = true) @RequestHeader(
                                                   value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return feed.feed(principal.userId(), filter, cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/requests/{id}")
   @Operation(summary = "Заявка для отклика (39, переход из пуша)")
   public MasterFeedItemDto one(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                      required = false) String language) {
      return feed.one(principal.userId(), id, Langs.fromHeader(language));
   }

   @PostMapping("/requests/{id}/seen")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Мастер открыл заявку или нажал на пуш", description = "Для «Посмотрели» в статистике клиента")
   public void seen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      feed.seen(principal.userId(), id);
   }

   @PostMapping("/requests/{id}/offer")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "«Могу помочь» или «Не моё» (39)", description = """
         CAN_HELP: priceFrom, availableAt, message — по желанию; клиенту пуш, открывается чат (chatId в ответе).
         NOT_MINE — клиент видит только число «Не их профиль». Ответ один.""")
   @ApiResponse(responseCode = "409", description = "REQUEST_EXPIRED, REQUEST_CLOSED, ALREADY_ANSWERED")
   public ServiceOfferDto offer(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Valid @RequestBody ServiceInputs.Offer request) {
      return feed.offer(principal.userId(), id, request);
   }

   @GetMapping("/stats")
   @Operation(summary = "Статистика мастера", description = "Последние 7 (WEEK) или 30 (MONTH) дней")
   public MasterStatsDto stats(@AuthenticationPrincipal AuthPrincipal principal,
                               @RequestParam(defaultValue = "WEEK") StatsPeriod period,
                               @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                     required = false) String language) {
      return stats.forOwner(principal.userId(), period, Langs.fromHeader(language));
   }
}
