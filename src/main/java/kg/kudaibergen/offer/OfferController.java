package kg.kudaibergen.offer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.offer.dto.BulkReplyRequest;
import kg.kudaibergen.offer.dto.BulkReplyResult;
import kg.kudaibergen.offer.dto.CreateOfferRequest;
import kg.kudaibergen.offer.dto.OfferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offers")
@Tag(name = "Предложения")
public class OfferController {

   private final OfferService offerService;

   public OfferController(OfferService offerService) {
      this.offerService = offerService;
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasRole('SELLER')")
   @Idempotent
   @Operation(summary = "Ответить на запрос")
   public OfferResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                               @Parameter(description = "Защита от дублей при обрыве связи")
                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                               @Valid @RequestBody CreateOfferRequest request) {
      return offerService.create(principal.userId(), request);
   }

   @PostMapping("/bulk")
   @PreAuthorize("hasRole('SELLER')")
   @Idempotent
   @Operation(summary = "Массовый ответ шаблоном на несколько запросов")
   public BulkReplyResult bulkReply(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Parameter(description = "Защита от дублей при обрыве связи")
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @Valid @RequestBody BulkReplyRequest request) {
      return offerService.bulkReply(principal.userId(), request);
   }
}
