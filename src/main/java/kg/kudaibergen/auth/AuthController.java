package kg.kudaibergen.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kg.kudaibergen.auth.dto.RefreshRequest;
import kg.kudaibergen.auth.dto.SendOtpRequest;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.auth.dto.TokenResponse;
import kg.kudaibergen.auth.dto.VerifyOtpRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Авторизация")
public class AuthController {

   private final AuthService authService;

   public AuthController(AuthService authService) {
      this.authService = authService;
   }

   @PostMapping("/otp/send")
   @Operation(summary = "Отправить SMS-код (экран 01, «Отправить снова» на 02)",
         description = "Код 4 цифры живёт 2 минуты; повторно — через 42 секунды; лимиты на номер и IP")
   @ApiResponse(responseCode = "429", description = "OTP_COOLDOWN или OTP_RATE_LIMITED, retryAfter в секундах")
   public SendOtpResponse send(@Valid @RequestBody SendOtpRequest request, HttpServletRequest http) {
      return authService.sendOtp(request, http.getRemoteAddr());
   }

   @PostMapping("/otp/verify")
   @Operation(summary = "Проверить код и войти (экран 02)",
         description = "Новый номер регистрируется автоматически; isNewUser=true — показать выбор роли (03)")
   @ApiResponse(responseCode = "400", description = "OTP_INVALID (attemptsLeft), OTP_EXPIRED, OTP_ATTEMPTS_EXCEEDED")
   public TokenResponse verify(@Valid @RequestBody VerifyOtpRequest request) {
      return authService.verifyOtp(request);
   }

   @PostMapping("/refresh")
   @Operation(summary = "Обменять refresh-токен на новую пару", description = "Старый refresh-токен гасится")
   @ApiResponse(responseCode = "401", description = "REFRESH_TOKEN_INVALID")
   public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
      return authService.refresh(request.refreshToken());
   }

   @PostMapping("/logout")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Выйти (экраны 19, 21)", description = "Гасит refresh-токен этого устройства")
   public void logout(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody RefreshRequest request) {
      authService.logout(principal.userId(), request.refreshToken());
   }
}
