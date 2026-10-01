package kg.kudaibergen.admin.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import org.springframework.lang.Nullable;

/** Сотрудники админки (право STAFF_MANAGE). */
public final class StaffDtos {

   private StaffDtos() {
   }

   /** hasPassword = false — сотрудник ещё не задал пароль (вход по ссылке «Задать пароль» с SMS-кодом). */
   public record StaffDto(Long userId, @MaskedPhone String phone, String fullName, String title, AdminRole role,
                          boolean active, boolean hasPassword, @Nullable Instant lastLoginAt, Instant createdAt,
                          @Nullable String createdByName) {
   }

   public record CreateStaffRequest(
         @NotBlank @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String phone,
         @NotBlank @Size(max = 120) String fullName,
         @Size(max = 80) String title,
         @NotNull AdminRole role) {
   }

   /** null — не менять. */
   public record UpdateStaffRequest(AdminRole role, @Size(min = 1, max = 80) String title,
                                    @Size(min = 1, max = 120) String fullName, Boolean isActive) {
   }

   /** Права роли. SUPER_ADMIN — все и не меняются (editable = false). */
   public record RolePermissionsDto(AdminRole role, String defaultTitle, boolean editable,
                                    List<AdminPermission> permissions) {
   }

   public record RolesDto(List<RolePermissionsDto> roles, List<AdminPermission> allPermissions) {
   }

   public record RolePermissionsRequest(@NotNull Set<AdminPermission> permissions) {
   }
}
