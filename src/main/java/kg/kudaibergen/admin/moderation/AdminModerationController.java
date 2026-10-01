package kg.kudaibergen.admin.moderation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintCounts;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintDetailDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintRow;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ResolveComplaintRequest;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ResolvedComplaintDto;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.complaint.ComplaintStatus;
import kg.kudaibergen.complaint.ComplaintType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Модерация [A4]: жалобы из приложений. */
@RestController
@RequestMapping("/api/v1/admin/complaints")
@Tag(name = "Админка: модерация")
public class AdminModerationController {

   private final AdminModerationService moderation;

   public AdminModerationController(AdminModerationService moderation) {
      this.moderation = moderation;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('COMPLAINTS_VIEW')")
   @Operation(summary = "Лента жалоб", description = "status: OPEN (новые) / RESOLVED / REJECTED, не задан — все. "
         + "counts — по статусам с учётом type")
   public AdminPage<ComplaintRow, ComplaintCounts> list(@RequestParam(required = false) ComplaintStatus status,
                                                        @RequestParam(required = false) ComplaintType type,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer limit) {
      return moderation.list(status, type, cursor, limit);
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('COMPLAINTS_VIEW')")
   @Operation(summary = "Жалоба: объект, ответственный и его статистика за 90 дней, заявитель, связанный запрос")
   public ComplaintDetailDto detail(@PathVariable Long id) {
      return moderation.detail(id);
   }

   @PostMapping("/{id}/resolve")
   @PreAuthorize("hasAuthority('COMPLAINTS_RESOLVE')")
   @Audited(action = "COMPLAINT_RESOLVE", entity = "COMPLAINT", id = "#id", comment = "#request.comment")
   @Operation(summary = "Решить жалобу", description = """
         REMOVE_CONTENT — скрыть объект (нужно CONTENT_REMOVE); WARN_SELLER — предупредить ответственного (SELLER_WARN);
         BLOCK_SHOP — заблокировать ответственного: магазин, мастера или пользователя (SELLERS_BLOCK / MASTERS_BLOCK /
         USERS_BLOCK); UNFOUNDED — необоснованна. comment уходит в журнал и ответственному. Открытые жалобы на тот же
         объект закрываются этим же решением; заявителям — пуш COMPLAINT_RESOLVED.""")
   @ApiResponse(responseCode = "400", description = "NOTHING_TO_REMOVE — магазин, мастер или чат не скрываются")
   @ApiResponse(responseCode = "403", description = "Нет права на выбранное действие")
   @ApiResponse(responseCode = "409", description = "COMPLAINT_RESOLVED, NO_PARTY, ALREADY_BLOCKED")
   public ResolvedComplaintDto resolve(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                       @Valid @RequestBody ResolveComplaintRequest request) {
      AuditTrail.before(moderation.detail(id));
      return moderation.resolve(id, request.action(), request.comment(), admin);
   }
}
