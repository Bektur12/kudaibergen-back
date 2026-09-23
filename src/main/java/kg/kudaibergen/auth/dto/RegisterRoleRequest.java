package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.user.entity.UserRole;

public record RegisterRoleRequest(
      @NotNull(message = "Укажите роль") UserRole role,
      @Size(max = 120) String name,
      @Size(max = 120) String storeName,
      @Size(max = 40) String businessType,
      @Size(max = 80) String city) {
}
