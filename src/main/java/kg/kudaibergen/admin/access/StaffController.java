package kg.kudaibergen.admin.access;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.access.StaffDtos.CreateStaffRequest;
import kg.kudaibergen.admin.access.StaffDtos.RolePermissionsRequest;
import kg.kudaibergen.admin.access.StaffDtos.RolesDto;
import kg.kudaibergen.admin.access.StaffDtos.StaffDto;
import kg.kudaibergen.admin.access.StaffDtos.UpdateStaffRequest;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Сотрудники и права ролей — только STAFF_MANAGE (по умолчанию — суперадмин). */
@RestController
@RequestMapping("/api/v1/admin/staff")
@Tag(name = "Админка: сотрудники")
public class StaffController {

   private final StaffService staff;

   public StaffController(StaffService staff) {
      this.staff = staff;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('STAFF_MANAGE')")
   @Operation(summary = "Сотрудники")
   public List<StaffDto> list() {
      return staff.list();
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('STAFF_MANAGE')")
   @Audited(action = "STAFF_CREATE", entity = "STAFF")
   @Operation(summary = "Добавить сотрудника", description = "Пользователь по телефону создаётся, если его нет. Пароль "
         + "сотрудник задаёт сам: «Задать пароль» на экране входа (SMS-код)")
   @ApiResponse(responseCode = "409", description = "STAFF_EXISTS")
   public StaffDto create(@Valid @RequestBody CreateStaffRequest request, @AuthenticationPrincipal AuthPrincipal admin) {
      StaffDto created = staff.create(request, admin.userId());
      AuditTrail.entityId(created.userId());
      return created;
   }

   @PatchMapping("/{userId}")
   @PreAuthorize("hasAuthority('STAFF_MANAGE')")
   @Audited(action = "STAFF_UPDATE", entity = "STAFF", id = "#userId")
   @Operation(summary = "Изменить роль, подпись, имя, доступ", description = "Себя и последнего суперадмина отключить или "
         + "понизить нельзя. Смена роли или отключение закрывают сессии сотрудника")
   @ApiResponse(responseCode = "409", description = "STAFF_SELF, LAST_SUPER_ADMIN")
   public StaffDto update(@PathVariable Long userId, @Valid @RequestBody UpdateStaffRequest request,
                          @AuthenticationPrincipal AuthPrincipal admin) {
      AuditTrail.before(staff.get(userId));
      return staff.update(userId, request, admin.userId());
   }

   @GetMapping("/roles")
   @PreAuthorize("hasAuthority('STAFF_MANAGE')")
   @Operation(summary = "Роли и их права", description = "allPermissions — все права для галочек")
   public RolesDto roles() {
      return staff.roles();
   }

   @PutMapping("/roles/{role}/permissions")
   @PreAuthorize("hasAuthority('STAFF_MANAGE')")
   @Audited(action = "ROLE_PERMISSIONS_SET", entity = "ROLE", comment = "#role.name()")
   @Operation(summary = "Права роли целиком", description = "Действует сразу для всех сотрудников роли. SUPER_ADMIN не "
         + "меняется")
   @ApiResponse(responseCode = "400", description = "ROLE_FIXED")
   public RolesDto setRolePermissions(@PathVariable AdminRole role, @Valid @RequestBody RolePermissionsRequest request) {
      AuditTrail.before(staff.roles());
      return staff.setRolePermissions(role, request.permissions());
   }
}
