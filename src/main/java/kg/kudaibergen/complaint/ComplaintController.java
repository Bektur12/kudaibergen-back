package kg.kudaibergen.complaint;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.market.MarketMapService;
import org.springframework.http.HttpStatus;
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
   private final MarketMapService market;

   public ComplaintController(ComplaintService complaints, MarketMapService market) {
      this.complaints = complaints;
      this.market = market;
   }

   @PostMapping("/market/containers/{id}/claim")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "«Это мой контейнер»", description = "Контейнер занят в приложении другим магазином — жалоба админу рынка")
   public ComplaintDto claimContainer(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) TextRequest request) {
      market.container(id);
      return complaints.create(principal.userId(), ComplaintType.CONTAINER_CLAIM, id,
            request == null ? null : request.text());
   }

   @GetMapping("/admin/complaints")
   @Operation(summary = "Открытые жалобы (админ рынка)")
   public List<ComplaintDto> open(@RequestParam(defaultValue = "100") int limit) {
      return complaints.open(Math.min(Math.max(limit, 1), 500));
   }

   @PostMapping("/admin/complaints/{id}/resolve")
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
