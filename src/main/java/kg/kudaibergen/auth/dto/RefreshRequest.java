package kg.kudaibergen.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "Укажите refresh-токен") String refreshToken) {
}
