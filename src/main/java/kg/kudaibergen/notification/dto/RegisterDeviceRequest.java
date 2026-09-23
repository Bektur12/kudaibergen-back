package kg.kudaibergen.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
      @NotBlank(message = "Укажите токен устройства") @Size(max = 255) String token,
      @NotBlank @Pattern(regexp = "IOS|ANDROID", message = "Платформа: IOS или ANDROID") String platform) {
}
