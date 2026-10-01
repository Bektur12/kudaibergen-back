package kg.kudaibergen.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kg.kudaibergen.auth.dto.ConfirmDeletionRequest;
import kg.kudaibergen.auth.dto.DeletionResponse;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Удаление аккаунта из профиля (ТЗ, раздел 3): код по SMS → подтверждение → стирание через 30 дней. */
@RestController
@RequestMapping("/api/v1/me/deletion")
@Tag(name = "Профиль")
public class AccountController {

   private final AuthService authService;

   public AccountController(AuthService authService) {
      this.authService = authService;
   }

   @PostMapping("/otp")
   @Operation(summary = "Отправить код для удаления аккаунта")
   public SendOtpResponse sendCode(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest http) {
      return authService.sendDeletionOtp(principal.userId(), http.getRemoteAddr());
   }

   @PostMapping
   @Operation(summary = "Подтвердить удаление аккаунта",
         description = "Закрывает все сессии; данные стираются через 30 дней, вход до этого срока отменяет удаление")
   public DeletionResponse confirm(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody ConfirmDeletionRequest request) {
      return authService.confirmDeletion(principal.userId(), request.code());
   }
}
