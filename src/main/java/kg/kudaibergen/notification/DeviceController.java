package kg.kudaibergen.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.notification.dto.RegisterDeviceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Tag(name = "Устройства")
public class DeviceController {

   private final DeviceService deviceService;

   public DeviceController(DeviceService deviceService) {
      this.deviceService = deviceService;
   }

   @PostMapping
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Зарегистрировать push-токен")
   public void register(@AuthenticationPrincipal AuthPrincipal principal,
                        @Valid @RequestBody RegisterDeviceRequest request) {
      deviceService.register(principal.userId(), request);
   }

   @DeleteMapping("/{token}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить push-токен")
   public void unregister(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String token) {
      deviceService.unregister(principal.userId(), token);
   }
}
