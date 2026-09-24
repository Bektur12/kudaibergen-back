package kg.kudaibergen.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.request.dto.CreateRequestRequest;
import kg.kudaibergen.request.dto.CreateRequestResponse;
import kg.kudaibergen.request.dto.RequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/requests")
@Validated
@Tag(name = "Запросы (покупатель)")
public class RequestController {

   private final RequestService requestService;

   public RequestController(RequestService requestService) {
      this.requestService = requestService;
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Создать запрос и разослать его магазинам")
   public CreateRequestResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Parameter(description = "Защита от дублей при обрыве связи")
                                       @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                       @Valid @RequestBody CreateRequestRequest request) {
      return requestService.create(principal.userId(), request);
   }

   @GetMapping("/my")
   @Operation(summary = "Свои запросы")
   public PageResponse<RequestResponse> my(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      return requestService.my(principal.userId(), page, size);
   }

   @GetMapping("/{id}")
   @Operation(summary = "Запрос вместе со списком предложений")
   public RequestResponse details(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requestService.details(id, principal.userId());
   }

   @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
   @Operation(summary = "Загрузить фото детали (одно на запрос, повтор заменяет предыдущее)")
   public RequestResponse uploadPhoto(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long id, @RequestParam MultipartFile file) {
      return requestService.uploadPhoto(id, principal.userId(), file);
   }

   @PostMapping("/{id}/extend")
   @Operation(summary = "Продлить запрос на 24 часа")
   public RequestResponse extend(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requestService.extend(id, principal.userId());
   }

   @PostMapping("/{id}/cancel")
   @Operation(summary = "Отменить запрос")
   public RequestResponse cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requestService.cancel(id, principal.userId());
   }
}
