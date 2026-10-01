package kg.kudaibergen.complaint;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.shop.dispute.ContainerDispute;
import kg.kudaibergen.shop.dispute.ContainerDisputeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Жалобы")
public class ComplaintController {

   private final ComplaintService complaints;
   private final ContainerDisputeService disputes;

   public ComplaintController(ComplaintService complaints, ContainerDisputeService disputes) {
      this.complaints = complaints;
      this.disputes = disputes;
   }

   @PostMapping("/market/containers/{id}/claim")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "«Это мой контейнер»", description = "Контейнер занят в приложении другим магазином — спор "
         + "уходит админу рынка. 409 CONTAINER_FREE — контейнер свободен, 400 OWN_CONTAINER — там ваш магазин")
   public ComplaintDto claimContainer(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) TextRequest request) {
      ContainerDispute dispute = disputes.claim(principal.userId(), id, request == null ? null : request.text());
      return new ComplaintDto(dispute.getId(), dispute.getClaimantUserId(), ComplaintType.CONTAINER_CLAIM,
            dispute.getContainerId(), dispute.getText(), ComplaintStatus.OPEN, null, dispute.getCreatedAt(),
            ComplaintReason.WRONG_PLACE);
   }

   @PostMapping("/complaints")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Пожаловаться", description = """
         На запчасть, магазин, фото места, отзыв, сообщение или чат, мастера, отклик мастера. Решает администрация;
         повторная жалоба на то же не создаёт новую. relatedRequestId / relatedServiceRequestId — запрос или заявка,
         из которых пришли (если есть).""")
   @ApiResponse(responseCode = "400", description = "BAD_COMPLAINT_TYPE")
   @ApiResponse(responseCode = "404", description = "COMPLAINT_TARGET_NOT_FOUND")
   @ApiResponse(responseCode = "429", description = "COMPLAINTS_RATE_LIMITED — больше 20 в сутки")
   public ComplaintDto complain(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody CreateComplaintRequest request) {
      return complaints.create(principal.userId(), request.type(), request.targetId(), request.reason(),
            request.text(), request.relatedRequestId(), request.relatedServiceRequestId());
   }

   public record TextRequest(@Size(max = 1000) String text) {
   }

   public record CreateComplaintRequest(@NotNull ComplaintType type, @NotNull Long targetId,
                                        @NotNull ComplaintReason reason, @Size(max = 1000) String text,
                                        Long relatedRequestId, Long relatedServiceRequestId) {
   }
}
