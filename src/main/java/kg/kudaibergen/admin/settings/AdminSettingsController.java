package kg.kudaibergen.admin.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.shop.ApprovalSettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Настройки: авто-активация продавцов и мастеров при регистрации. */
@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "Админка: настройки")
public class AdminSettingsController {

   /** sellersAutoApprove — новый магазин действует сразу; false — ждёт «Подтвердить» в разделе «Продавцы». */
   public record AdminSettingsDto(boolean sellersAutoApprove, boolean mastersAutoApprove) {
   }

   /** null — не менять. */
   public record AdminSettingsPatch(Boolean sellersAutoApprove, Boolean mastersAutoApprove) {
   }

   private final ApprovalSettings approvals;

   public AdminSettingsController(ApprovalSettings approvals) {
      this.approvals = approvals;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Настройки")
   public AdminSettingsDto get() {
      return new AdminSettingsDto(approvals.sellersAutoApprove(), approvals.mastersAutoApprove());
   }

   @PatchMapping
   @PreAuthorize("hasAuthority('SELLERS_VERIFY')")
   @Audited(action = "SETTINGS_UPDATE", entity = "SETTINGS")
   @Operation(summary = "Изменить настройки", description = "Действует на новые регистрации; те, кто уже ждёт "
         + "проверки, остаются ждать — их подтверждают вручную")
   public AdminSettingsDto update(@AuthenticationPrincipal AuthPrincipal admin, @RequestBody AdminSettingsPatch patch) {
      AuditTrail.before(get());
      if (patch.sellersAutoApprove() != null) {
         approvals.set(ApprovalSettings.SELLERS, patch.sellersAutoApprove(), admin.userId());
      }
      if (patch.mastersAutoApprove() != null) {
         approvals.set(ApprovalSettings.MASTERS, patch.mastersAutoApprove(), admin.userId());
      }
      return get();
   }
}
