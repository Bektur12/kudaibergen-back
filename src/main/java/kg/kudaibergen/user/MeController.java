package kg.kudaibergen.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.dto.ChangeRoleRequest;
import kg.kudaibergen.user.dto.MeResponse;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.dto.SettingsResponse;
import kg.kudaibergen.user.dto.UpdateMeRequest;
import kg.kudaibergen.user.dto.UpdateSettingsRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Профиль")
public class MeController {

   private final UserService userService;

   private final MeView meView;

   public MeController(UserService userService, MeView meView) {
      this.userService = userService;
      this.meView = meView;
   }

   @DeleteMapping("/avatar")
   @Operation(summary = "Убрать аватар", description = "Клиент рисует первую букву имени")
   public MeResponse removeAvatar(@AuthenticationPrincipal AuthPrincipal principal) {
      return me(userService.removeAvatar(principal.userId()));
   }

   private MeResponse me(User user) {
      return meView.of(user);
   }

   @GetMapping
   @Operation(summary = "Профиль текущего пользователя", description = "Экраны 05, 19, 21")
   public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
      return me(userService.getRequired(principal.userId()));
   }

   @PatchMapping
   @Operation(summary = "Изменить имя, язык и аватар", description = "Экран 19: RU / KG; avatarMediaId — из POST /media/photos")
   public MeResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody UpdateMeRequest request) {
      return me(userService.updateProfile(principal.userId(), request));
   }

   @PutMapping("/role")
   @Operation(summary = "Выбрать режим: покупатель или продавец",
         description = "Экран 03, а также «Я продавец — открыть бокс» (19) и «Перейти в режим покупателя» (21)")
   public MeResponse changeRole(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody ChangeRoleRequest request) {
      return me(userService.changeRole(principal.userId(), request.role()));
   }

   @GetMapping("/settings")
   @Operation(summary = "Настройки уведомлений и темы")
   public SettingsResponse settings(@AuthenticationPrincipal AuthPrincipal principal) {
      return SettingsResponse.of(userService.settings(principal.userId()));
   }

   @PatchMapping("/settings")
   @Operation(summary = "Изменить настройки", description = "Экран 19 «Уведомления об ответах», 21 «Звук нового запроса»")
   public SettingsResponse updateSettings(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody UpdateSettingsRequest request) {
      return SettingsResponse.of(userService.updateSettings(principal.userId(), request));
   }
}
