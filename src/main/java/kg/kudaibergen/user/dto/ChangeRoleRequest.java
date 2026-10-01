package kg.kudaibergen.user.dto;

import jakarta.validation.constraints.NotNull;
import kg.kudaibergen.user.entity.UserRole;

public record ChangeRoleRequest(@NotNull(message = "Укажите роль") UserRole role) {
}
