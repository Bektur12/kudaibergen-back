package kg.kudaibergen.request;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.request.dto.RecipientsEstimateDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestDetailDto;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.dto.RequestStatsDto;
import kg.kudaibergen.request.dto.RequestSummaryDto;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.request.dto.WidenResultDto;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.RequestTarget;
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

/** Запрос «Найти запчасть» — сторона покупателя (экраны 05, 06, 06б, 07, 09, 20, 31, 32). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Запросы: покупатель")
public class RequestController {

   private final RequestService requests;

   public RequestController(RequestService requests) {
      this.requests = requests;
   }

   @GetMapping("/requests/estimate")
   @Operation(summary = "Сколько продавцов получат запрос (06б, 31)", description = """
         «Запрос получат 18 продавцов по Toyota» — клиент запрашивает при каждом изменении выбора.
         carId из гаража или brandId (без гаража). rowIds — для ROWS (1–10), containerIds — для CONTAINERS (1–30):
         контейнерам запрос уходит без фильтра по марке.""")
   public RecipientsEstimateDto estimate(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(required = false) Long carId,
                                         @RequestParam(required = false) Long brandId,
                                         @RequestParam RequestTarget target,
                                         @RequestParam(required = false) List<Long> rowIds,
                                         @RequestParam(required = false) List<Long> containerIds) {
      return requests.estimate(principal.userId(), carId, brandId, target, rowIds, containerIds);
   }

   @PostMapping("/requests")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Отправить запрос (06, 06б)", description = """
         MARKET и ROWS — проверенным, открытым сейчас боксам с маркой машины (в выбранных рядах); CONTAINERS —
         выбранным открытым боксам без фильтра по марке. Им — пуш с кнопками «Есть / Нет». Запрос активен
         duration (по умолчанию MIN_30; END_OF_DAY — до 17:00, если позже — 3 часа), потом EXPIRED.
         Поддерживает Idempotency-Key: без сети клиент повторяет отправку тем же ключом.""")
   @ApiResponse(responseCode = "409", description = "OPEN_REQUESTS_LIMIT — уже 10 активных запросов")
   @ApiResponse(responseCode = "429", description = "DAILY_REQUESTS_LIMIT — 20 запросов за сутки, retryAfter")
   public RequestDetailDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody RequestInputs.CreateRequest request,
                                  @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                        required = false) String language) {
      return requests.create(principal.userId(), request, Langs.fromHeader(language));
   }

   @GetMapping("/requests/my")
   @Operation(summary = "Мои запросы (05)", description = "Активные сверху, истёкшие и закрытые ниже; status — только один статус")
   public CursorPage<RequestSummaryDto> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                             @RequestParam(required = false) RequestStatus status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit) {
      return requests.mine(principal.userId(), status, cursor, limit);
   }

   @GetMapping("/requests/{id}")
   @Operation(summary = "Запрос (07, 09, 20)", description = """
         state: WAITING, HAS_ANSWERS — активен; NO_ANSWERS — время вышло без «Есть» (20); EXPIRED — время вышло,
         ответы есть; CLOSED""")
   public RequestDetailDto detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                        required = false) String language) {
      return requests.detail(principal, id, Langs.fromHeader(language));
   }

   @GetMapping("/requests/{id}/replies")
   @Operation(summary = "Кто ответил «Есть» (07)",
         description = "В порядке ответа; «Нет» не показываются. afterId — только ответы новее этого id")
   public List<ReplyDto> replies(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                 @RequestParam(required = false) Long afterId) {
      return requests.replies(principal, id, afterId);
   }

   @GetMapping("/requests/{id}/stats")
   @Operation(summary = "Статистика запроса (32)", description = """
         Доставлено, посмотрели, «Есть», «Нет», ещё не ответили; «Есть» — с магазином, местом, ценой и чатом,
         «Нет» — только ряд и контейнер. Живое обновление — событие REQUEST_STATS в канале inbox покупателя.""")
   public RequestStatsDto stats(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requests.stats(principal, id);
   }

   @PostMapping("/requests/{id}/extend")
   @Operation(summary = "Продлить запрос (32)", description = """
         minutes: 30, 60 или 180, не больше 3 раз. Активный — срок сдвигается; истёкший — снова активен
         на minutes от сейчас, продавцы без ответа снова могут ответить.""")
   @ApiResponse(responseCode = "409", description = "EXTEND_LIMIT, REQUEST_CLOSED, OPEN_REQUESTS_LIMIT")
   public RequestDetailDto extend(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody RequestInputs.Extend request,
                                  @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                        required = false) String language) {
      return requests.extend(principal, id, request, Langs.fromHeader(language));
   }

   @PostMapping("/requests/{id}/close")
   @Operation(summary = "Закрыть запрос (09, 20)", description = """
         shopId — у кого купил (только бокс, ответивший «Есть»), stars 1–5 и tags — оценка этому боксу.
         Без shopId — просто закрыть. Можно и после истечения времени. Остальные продавцы запрос больше не видят.""")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED")
   public RequestDetailDto close(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                 @Valid @RequestBody RequestInputs.CloseRequest request,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                       required = false) String language) {
      return requests.close(principal, id, request, Langs.fromHeader(language));
   }

   @PostMapping("/requests/{id}/widen")
   @Operation(summary = "Отправить всему рынку (20, 32)", description = """
         Тело {target: MARKET} или пустое. Добавляет боксы марки, которым запрос ещё не приходил (им — пуш);
         срок отсчитывается заново, истёкший запрос снова активен.""")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED, OPEN_REQUESTS_LIMIT")
   public WidenResultDto widen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                               @RequestBody(required = false) RequestInputs.Widen request) {
      return requests.widen(principal, id, request);
   }

   @GetMapping("/reviews/tags")
   @Operation(summary = "Теги оценки (09)")
   public List<ReviewTagDto> reviewTags(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
         required = false) String language) {
      return requests.reviewTags(Langs.fromHeader(language));
   }
}
