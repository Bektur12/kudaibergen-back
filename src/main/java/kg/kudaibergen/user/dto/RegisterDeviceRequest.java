package kg.kudaibergen.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.user.entity.Platform;

public record RegisterDeviceRequest(
      @NotBlank(message = "Укажите FCM-токен") @Size(max = 255) String token,
      @NotNull(message = "Укажите платформу") Platform platform,
      @Schema(description = "Приложение само рисует пуши с кнопками («Есть / Нет»). Старые версии не присылают — им на Android обычный пуш")
      Boolean actions,
      @Schema(description = "iPhone, iOS 17.2+: push-to-start токен Live Activity — карточка запроса на экране блокировки")
      @Size(max = 255) String liveActivityToken) {
}
