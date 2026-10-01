package kg.kudaibergen.admin.masters;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterDetailDto;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterRow;
import kg.kudaibergen.admin.masters.AdminMasterDtos.CreateMasterRequest;
import kg.kudaibergen.admin.masters.AdminMasterDtos.MasterReasonRequest;
import kg.kudaibergen.admin.masters.AdminMasterDtos.MasterTabCounts;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminMessageRequest;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminMessageSentDto;
import kg.kudaibergen.common.security.AuthPrincipal;
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

/** Мастера и СТО [A7]. */
@RestController
@RequestMapping("/api/v1/admin/masters")
@Tag(name = "Админка: мастера")
public class AdminMasterController {

   private final AdminMasterService masters;

   public AdminMasterController(AdminMasterService masters) {
      this.masters = masters;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('MASTERS_VIEW')")
   @Operation(summary = "Список мастеров с табами", description = "service — код услуги, brandId — работает с маркой "
         + "(или со всеми), q — название, адрес, цифры телефона. counts — по табам с учётом фильтров")
   public AdminPage<AdminMasterRow, MasterTabCounts> list(@RequestParam(defaultValue = "ALL") MasterTab tab,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(required = false) String service,
                                                          @RequestParam(required = false) Long brandId,
                                                          @RequestParam(required = false) String cursor,
                                                          @RequestParam(required = false) Integer limit) {
      return masters.list(tab, q, service, brandId, cursor, limit);
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('MASTERS_VIEW')")
   @Operation(summary = "Карточка мастера")
   public AdminMasterDetailDto detail(@PathVariable Long id) {
      return masters.detail(id);
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('MASTERS_CREATE')")
   @Audited(action = "MASTER_CREATE", entity = "MASTER")
   @Operation(summary = "Добавить мастера вручную", description = "Пользователь по телефону создаётся, если его нет; "
         + "профиль сразу действует")
   @ApiResponse(responseCode = "409", description = "ALREADY_MASTER")
   public AdminMasterDetailDto create(@Valid @RequestBody CreateMasterRequest request) {
      Long id = masters.create(request);
      AuditTrail.entityId(id);
      return masters.detail(id);
   }

   @PostMapping("/{id}/approve")
   @PreAuthorize("hasAuthority('MASTERS_VERIFY')")
   @Audited(action = "MASTER_APPROVE", entity = "MASTER", id = "#id")
   @Operation(summary = "Подтвердить профиль", description = "На проверке или отклонённый → действует; пуш ACCOUNT_STATUS")
   @ApiResponse(responseCode = "409", description = "NOTHING_TO_VERIFY")
   public AdminMasterDetailDto approve(@PathVariable Long id) {
      AuditTrail.before(masters.detail(id));
      masters.approve(id);
      return masters.detail(id);
   }

   @PostMapping("/{id}/reject")
   @PreAuthorize("hasAuthority('MASTERS_VERIFY')")
   @Audited(action = "MASTER_REJECT", entity = "MASTER", id = "#id", comment = "#request.reason")
   @Operation(summary = "Отклонить профиль", description = "Заявки не приходят, мастер видит причину")
   @ApiResponse(responseCode = "409", description = "NOTHING_TO_VERIFY")
   public AdminMasterDetailDto reject(@PathVariable Long id, @Valid @RequestBody MasterReasonRequest request) {
      AuditTrail.before(masters.detail(id));
      masters.reject(id, request.reason());
      return masters.detail(id);
   }

   @PostMapping("/{id}/block")
   @PreAuthorize("hasAuthority('MASTERS_BLOCK')")
   @Audited(action = "MASTER_BLOCK", entity = "MASTER", id = "#id", comment = "#request.reason")
   @Operation(summary = "Заблокировать")
   @ApiResponse(responseCode = "409", description = "ALREADY_BLOCKED")
   public AdminMasterDetailDto block(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                     @Valid @RequestBody MasterReasonRequest request) {
      AuditTrail.before(masters.detail(id));
      masters.block(id, admin.userId(), request.reason());
      return masters.detail(id);
   }

   @PostMapping("/{id}/unblock")
   @PreAuthorize("hasAuthority('MASTERS_BLOCK')")
   @Audited(action = "MASTER_UNBLOCK", entity = "MASTER", id = "#id")
   @Operation(summary = "Снять блокировку")
   @ApiResponse(responseCode = "409", description = "NOT_BLOCKED")
   public AdminMasterDetailDto unblock(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
      AuditTrail.before(masters.detail(id));
      masters.unblock(id, admin.userId());
      return masters.detail(id);
   }

   @PostMapping("/{id}/warn")
   @PreAuthorize("hasAuthority('MASTERS_BLOCK')")
   @Audited(action = "MASTER_WARN", entity = "MASTER", id = "#id", comment = "#request.reason")
   @Operation(summary = "Предупредить", description = "Пуш с причиной; 90 дней мастер отмечен «Предупреждён»")
   public AdminMasterDetailDto warn(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                    @Valid @RequestBody MasterReasonRequest request) {
      masters.warn(id, admin.userId(), request.reason());
      return masters.detail(id);
   }

   @PostMapping("/{id}/message")
   @PreAuthorize("hasAuthority('MESSAGE_USERS')")
   @Audited(action = "MASTER_MESSAGE", entity = "MASTER", id = "#id", comment = "#request.text")
   @Operation(summary = "Написать мастеру", description = "Пуш ADMIN_MESSAGE; ответить нельзя")
   public AdminMessageSentDto message(@PathVariable Long id, @Valid @RequestBody AdminMessageRequest request) {
      return new AdminMessageSentDto(masters.message(id, request.text()));
   }
}
