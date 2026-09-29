package kg.kudaibergen.request;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.request.dto.IncomingRequestDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestInputs;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Входящие запросы бокса — сторона продавца (экраны 11, 12, 14). Владелец и сотрудники видят одну ленту. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Запросы: продавец")
public class IncomingRequestController {

   private final IncomingRequestService incoming;

   public IncomingRequestController(IncomingRequestService incoming) {
      this.incoming = incoming;
   }

   @GetMapping("/my/shop/requests")
   @Operation(summary = "Запросы бокса (11)", description = """
         NEW — активные без ответа (сверху, expiresAt — таймер «осталось 12 мин»), ANSWERED — «Вы ответили «есть»»
         (ниже), EXPIRED — время вышло без ответа, UNANSWERED — бокс не ответил, а время
         вышло или запрос закрыт («Смотреть» из статистики 17).
         Закрытый покупателем запрос остаётся только у бокса, где купили.""")
   public CursorPage<IncomingRequestDto> feed(@AuthenticationPrincipal AuthPrincipal principal,
                                              @RequestParam(defaultValue = "NEW") IncomingRequestService.Filter filter,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit,
                                              @Parameter(hidden = true) @RequestHeader(
                                                    value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return incoming.feed(principal.userId(), filter, cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/my/shop/requests/{id}")
   @Operation(summary = "Запрос для ответа (12, переход из пуша 14)")
   public IncomingRequestDto one(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                       required = false) String language) {
      return incoming.one(principal.userId(), id, Langs.fromHeader(language));
   }

   @GetMapping("/my/shop/requests/{id}/suggested-parts")
   @Operation(summary = "Подходящие свои запчасти для ответа «Есть» (12)",
         description = "Опубликованные запчасти бокса под машину запроса, сначала совпавшие с текстом. id — в partId ответа")
   public List<PartCardDto> suggestedParts(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return incoming.suggestedParts(principal.userId(), id);
   }

   @PostMapping("/my/shop/requests/{id}/seen")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Продавец открыл запрос или нажал на пуш", description = "Для «посмотрели» в статистике покупателя (32)")
   public void seen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      incoming.seen(principal.userId(), id);
   }

   @PostMapping("/requests/{id}/replies")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Ответить «Есть» или «Нет» (12, кнопки пуша 14)", description = """
         «Есть» требует condition; message до 300 символов, price и mediaIds (до 3 фото, purpose=REPLY) — по желанию.
         Покупатель получает пуш. «Нет» — запрос скрывается, покупатель видит только место в статистике.
         Первый ответ бокса засчитывается за магазин. После expiresAt ответить нельзя.""")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED, REQUEST_EXPIRED, ALREADY_REPLIED")
   @ApiResponse(responseCode = "403", description = "SHOP_NOT_ACTIVE — бокс не проверен или заблокирован")
   public ReplyDto reply(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                         @Valid @RequestBody RequestInputs.Reply request) {
      return incoming.reply(principal.userId(), id, request);
   }

   @PatchMapping("/requests/{id}/replies/mine")
   @Operation(summary = "Изменить ответ бокса", description = "В течение 10 минут после ответа, потом — только в чате")
   @ApiResponse(responseCode = "409", description = "REPLY_EDIT_EXPIRED, REQUEST_CLOSED, REQUEST_EXPIRED")
   public ReplyDto editReply(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                             @Valid @RequestBody RequestInputs.Reply request) {
      return incoming.editReply(principal.userId(), id, request);
   }
}
