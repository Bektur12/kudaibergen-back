package kg.kudaibergen.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.user.entity.Platform;

public record RegisterDeviceRequest(
      @NotBlank(message = "Укажите FCM-токен") @Size(max = 255) String token,
      @NotNull(message = "Укажите платформу") Platform platform) {
}
