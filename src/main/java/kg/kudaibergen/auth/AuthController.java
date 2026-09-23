package kg.kudaibergen.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.auth.dto.RefreshRequest;
import kg.kudaibergen.auth.dto.RegisterRoleRequest;
import kg.kudaibergen.auth.dto.RequestCodeRequest;
import kg.kudaibergen.auth.dto.RequestCodeResponse;
import kg.kudaibergen.auth.dto.TokenResponse;
import kg.kudaibergen.auth.dto.VerifyRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Авторизация")
public class AuthController {

   private final AuthService authService;

   public AuthController(AuthService authService) {
      this.authService = authService;
   }

   @PostMapping("/request-code")
   @Operation(summary = "Запросить SMS-код", description = "Не чаще одного раза в минуту на номер")
   public RequestCodeResponse requestCode(@Valid @RequestBody RequestCodeRequest request) {
      return authService.requestCode(request);
   }

   @PostMapping("/verify")
   @Operation(summary = "Проверить код и получить токены")
   public TokenResponse verify(@Valid @RequestBody VerifyRequest request) {
      return authService.verify(request);
   }

   @PostMapping("/refresh")
   @Operation(summary = "Обновить пару токенов")
   public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
      return authService.refresh(request);
   }

   @PostMapping("/register-role")
   @Operation(summary = "Выбрать роль и завершить регистрацию",
         description = "Для роли SELLER автоматически создаётся магазин")
   public TokenResponse registerRole(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody RegisterRoleRequest request) {
      return authService.registerRole(principal.userId(), request);
   }
}
