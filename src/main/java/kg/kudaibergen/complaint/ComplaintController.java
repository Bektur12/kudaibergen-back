package kg.kudaibergen.complaint;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.shop.dispute.ContainerDispute;
import kg.kudaibergen.shop.dispute.ContainerDisputeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            dispute.getContainerId(), dispute.getText(), ComplaintStatus.OPEN, null, dispute.getCreatedAt());
   }

   @GetMapping("/admin/complaints")
   @PreAuthorize("hasAuthority('COMPLAINTS_VIEW')")
   @Operation(summary = "Открытые жалобы (админ рынка)")
   public List<ComplaintDto> open(@RequestParam(defaultValue = "100") int limit) {
      return complaints.open(Math.min(Math.max(limit, 1), 500));
   }

   @PostMapping("/admin/complaints/{id}/resolve")
   @PreAuthorize("hasAuthority('COMPLAINTS_RESOLVE')")
   @Audited(action = "COMPLAINT_RESOLVE", entity = "COMPLAINT", id = "#id", comment = "#request.resolution")
   @Operation(summary = "Закрыть жалобу", description = "RESOLVED — меры приняты, REJECTED — жалоба необоснованна")
   public ComplaintDto resolve(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                               @Valid @RequestBody ResolveRequest request) {
      return complaints.resolve(id, request.status(), request.resolution(), admin.userId());
   }

   public record TextRequest(@Size(max = 1000) String text) {
   }

   public record ResolveRequest(@NotNull ComplaintStatus status, @Size(max = 500) String resolution) {
   }
}
