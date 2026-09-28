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
import kg.kudaibergen.request.dto.RecipientsPreviewDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestDetailDto;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.dto.RequestSummaryDto;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.request.dto.WidenResultDto;
import kg.kudaibergen.request.entity.RequestStatus;
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

/** Запрос «Найти запчасть» — сторона покупателя (экраны 05, 06, 07, 09, 20). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Запросы: покупатель")
public class RequestController {

   private final RequestService requests;

   public RequestController(RequestService requests) {
      this.requests = requests;
   }

   @PostMapping("/requests/recipients-preview")
   @Operation(summary = "Сколько продавцов получат запрос (06)",
         description = "«Запрос получат 43 продавца по Toyota». carId из гаража или brandId (без гаража)")
   public RecipientsPreviewDto preview(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody RequestInputs.RecipientsPreview request) {
      return requests.preview(principal.userId(), request);
   }

   @PostMapping("/requests")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Отправить запрос (06)", description = """
         Уходит проверенным, открытым сейчас боксам с маркой машины (и в выбранном ряду / боксе), им — пуш
         с кнопками «Есть / Нет». Поддерживает Idempotency-Key: без сети клиент повторяет отправку тем же ключом.""")
   @ApiResponse(responseCode = "409", description = "OPEN_REQUESTS_LIMIT — уже 10 открытых запросов")
   @ApiResponse(responseCode = "429", description = "DAILY_REQUESTS_LIMIT — 20 запросов за сутки, retryAfter")
   public RequestDetailDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody RequestInputs.CreateRequest request,
                                  @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                        required = false) String language) {
      return requests.create(principal.userId(), request, Langs.fromHeader(language));
   }

   @GetMapping("/requests/my")
   @Operation(summary = "Мои запросы (05)", description = "Открытые сверху, закрытые ниже; status — только один статус")
   public CursorPage<RequestSummaryDto> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                             @RequestParam(required = false) RequestStatus status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer limit) {
      return requests.mine(principal.userId(), status, cursor, limit);
   }

   @GetMapping("/requests/{id}")
   @Operation(summary = "Запрос (07, 09, 20)", description = "state: WAITING, HAS_ANSWERS, NO_ANSWERS, CLOSED, EXPIRED")
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

   @PostMapping("/requests/{id}/close")
   @Operation(summary = "Закрыть запрос (09, 20)", description = """
         shopId — у кого купил (только бокс, ответивший «Есть»), stars 1–5 и tags — оценка этому боксу.
         Без shopId — просто закрыть. Остальные продавцы запрос больше не видят.""")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED")
   public RequestDetailDto close(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                 @Valid @RequestBody RequestInputs.CloseRequest request,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                       required = false) String language) {
      return requests.close(principal, id, request, Langs.fromHeader(language));
   }

   @PostMapping("/requests/{id}/widen")
   @Operation(summary = "Отправить всему рынку (20)",
         description = "Добавляет боксы марки, которым запрос ещё не приходил; таймер «никто не ответил» заново")
   public WidenResultDto widen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requests.widen(principal, id);
   }

   @GetMapping("/reviews/tags")
   @Operation(summary = "Теги оценки (09)")
   public List<ReviewTagDto> reviewTags(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
         required = false) String language) {
      return requests.reviewTags(Langs.fromHeader(language));
   }
}
