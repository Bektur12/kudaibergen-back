package kg.kudaibergen.admin.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditCounts;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditEntryDto;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditRow;
import kg.kudaibergen.admin.common.AdminPage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Журнал действий админки. */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Админка: журнал")
public class AuditViewController {

   private final AuditViewService audit;

   public AuditViewController(AuditViewService audit) {
      this.audit = audit;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('AUDIT_VIEW')")
   @Operation(summary = "Журнал", description = "Фильтры: adminId, action, entityType, entityId, from, to (ISO UTC). "
         + "counts — сегодня и за 7 дней с учётом фильтров")
   public AdminPage<AuditRow, AuditCounts> list(@RequestParam(required = false) Long adminId,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) String entityType,
                                                @RequestParam(required = false) Long entityId,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit) {
      return audit.list(adminId, action, entityType, entityId, from, to, cursor, limit);
   }

   @GetMapping("/facets")
   @PreAuthorize("hasAuthority('AUDIT_VIEW')")
   @Operation(summary = "Значения для фильтров", description = "{actions: [...], entityTypes: [...]}")
   public Map<String, List<String>> facets() {
      return audit.facets();
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('AUDIT_VIEW')")
   @Operation(summary = "Запись: до и после")
   public AuditEntryDto get(@PathVariable Long id) {
      return audit.get(id);
   }
}
