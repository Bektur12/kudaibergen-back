package kg.kudaibergen.admin.broadcasts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.Audience;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastCounts;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastDto;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastFilters;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastInput;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastStatus;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.EstimateDto;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.ScheduleRequest;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Рассылки [A6]. Отправка — фоновая (BroadcastSender), с учётом тихих часов и языка получателя. */
@RestController
@RequestMapping("/api/v1/admin/broadcasts")
@Tag(name = "Админка: рассылки")
public class BroadcastController {

   private final BroadcastService broadcasts;

   public BroadcastController(BroadcastService broadcasts) {
      this.broadcasts = broadcasts;
   }

   @GetMapping("/estimate")
   @PreAuthorize("hasAuthority('BROADCASTS_VIEW')")
   @Operation(summary = "Сколько получат", description = "«Получат 64 продавца»; withDevices — у скольких есть пуши. "
         + "brandIds / rowIds / serviceTypes — через запятую")
   public EstimateDto estimate(@RequestParam Audience audience,
                               @RequestParam(required = false) java.util.List<Long> brandIds,
                               @RequestParam(required = false) java.util.List<Long> rowIds,
                               @RequestParam(required = false) java.util.List<String> serviceTypes) {
      return broadcasts.estimate(audience, new BroadcastFilters(brandIds, rowIds, serviceTypes));
   }

   @GetMapping
   @PreAuthorize("hasAuthority('BROADCASTS_VIEW')")
   @Operation(summary = "История рассылок", description = "Заголовок, кому, получили, % открыли")
   public AdminPage<BroadcastDto, BroadcastCounts> list(@RequestParam(required = false) BroadcastStatus status,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(required = false) Integer limit) {
      return broadcasts.list(status, cursor, limit);
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('BROADCASTS_VIEW')")
   @Operation(summary = "Рассылка")
   public BroadcastDto get(@PathVariable Long id) {
      return broadcasts.get(id);
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('BROADCASTS_SEND')")
   @Audited(action = "BROADCAST_CREATE", entity = "BROADCAST")
   @Operation(summary = "Черновик рассылки")
   @ApiResponse(responseCode = "400", description = "BAD_FILTER — фильтр не подходит к аудитории")
   public BroadcastDto create(@Valid @RequestBody BroadcastInput input, @AuthenticationPrincipal AuthPrincipal admin) {
      BroadcastDto created = broadcasts.create(input, admin.userId());
      AuditTrail.entityId(created.id());
      return created;
   }

   @PatchMapping("/{id}")
   @PreAuthorize("hasAuthority('BROADCASTS_SEND')")
   @Audited(action = "BROADCAST_UPDATE", entity = "BROADCAST", id = "#id")
   @Operation(summary = "Изменить черновик", description = "Все поля целиком, как при создании")
   @ApiResponse(responseCode = "409", description = "BROADCAST_NOT_DRAFT")
   public BroadcastDto update(@PathVariable Long id, @Valid @RequestBody BroadcastInput input) {
      AuditTrail.before(broadcasts.get(id));
      return broadcasts.update(id, input);
   }

   @PostMapping("/{id}/schedule")
   @PreAuthorize("hasAuthority('BROADCASTS_SEND')")
   @Audited(action = "BROADCAST_SCHEDULE", entity = "BROADCAST", id = "#id")
   @Operation(summary = "Запланировать или отправить сейчас", description = "{at} или {now: true}. Время в тихих часах "
         + "22:00–07:00 (Бишкек) переносится на 07:00 — тогда deferred = true")
   @ApiResponse(responseCode = "409", description = "BROADCAST_STARTED")
   public BroadcastDto schedule(@PathVariable Long id, @RequestBody ScheduleRequest request) {
      AuditTrail.before(broadcasts.get(id));
      return broadcasts.schedule(id, request.at(), Boolean.TRUE.equals(request.now()));
   }

   @PostMapping("/{id}/cancel")
   @PreAuthorize("hasAuthority('BROADCASTS_SEND')")
   @Audited(action = "BROADCAST_CANCEL", entity = "BROADCAST", id = "#id")
   @Operation(summary = "Отменить", description = "Черновик, запланированную или идущую — оставшиеся не получат")
   @ApiResponse(responseCode = "409", description = "BROADCAST_FINISHED")
   public BroadcastDto cancel(@PathVariable Long id) {
      AuditTrail.before(broadcasts.get(id));
      return broadcasts.cancel(id);
   }
}
