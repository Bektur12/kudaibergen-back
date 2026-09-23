package kg.kudaibergen.user;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.dto.CreateVehicleRequest;
import kg.kudaibergen.user.dto.MeResponse;
import kg.kudaibergen.user.dto.UpdateMeRequest;
import kg.kudaibergen.user.dto.UpdateVehicleRequest;
import kg.kudaibergen.user.dto.VehicleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Профиль")
public class MeController {

   private final UserService userService;
   private final VehicleService vehicleService;

   public MeController(UserService userService, VehicleService vehicleService) {
      this.userService = userService;
      this.vehicleService = vehicleService;
   }

   @GetMapping
   @Operation(summary = "Профиль текущего пользователя")
   public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
      return MeResponse.of(userService.getRequired(principal.userId()));
   }

   @PatchMapping
   @Operation(summary = "Обновить имя и город")
   public MeResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody UpdateMeRequest request) {
      return MeResponse.of(userService.updateProfile(principal.userId(), request));
   }

   @GetMapping("/vehicles")
   @Operation(summary = "Автомобили пользователя")
   public List<VehicleResponse> vehicles(@AuthenticationPrincipal AuthPrincipal principal) {
      return vehicleService.list(principal.userId()).stream().map(VehicleResponse::of).toList();
   }

   @PostMapping("/vehicles")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Добавить автомобиль")
   public VehicleResponse addVehicle(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody CreateVehicleRequest request) {
      return VehicleResponse.of(vehicleService.create(principal.userId(), request));
   }

   @PatchMapping("/vehicles/{id}")
   @Operation(summary = "Изменить автомобиль")
   public VehicleResponse updateVehicle(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateVehicleRequest request) {
      return VehicleResponse.of(vehicleService.update(id, principal.userId(), request));
   }

   @DeleteMapping("/vehicles/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить автомобиль")
   public void deleteVehicle(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      vehicleService.delete(id, principal.userId());
   }
}
