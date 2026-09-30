package kg.kudaibergen.admin.access;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminLoginRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminPasswordCodeRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminSetPasswordRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminVerifyRequest;
import kg.kudaibergen.admin.access.dto.AdminSessionDto;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.common.config.AdminProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вход в админку. Эндпоинты открыты (SecurityConfig), ограничены лимитами на номер и IP.
 * Refresh-токен — только в httpOnly-cookie {@value #COOKIE}: фронт шлёт запросы с credentials: 'include'.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Админка: вход")
public class AdminAuthController {

   public static final String COOKIE = "admin_refresh";
   private static final String COOKIE_PATH = "/api/v1/admin/auth";

   private final AdminAuthService auth;
   private final AdminProperties config;

   public AdminAuthController(AdminAuthService auth, AdminProperties config) {
      this.auth = auth;
      this.config = config;
   }

   @PostMapping("/login")
   @Operation(summary = "Шаг 1: телефон и пароль", description = "Верно — на телефон уходит SMS-код (4 цифры, 2 минуты)")
   @ApiResponse(responseCode = "401", description = "ADMIN_BAD_CREDENTIALS — неверный телефон или пароль, "
         + "нет доступа или пароль не задан (не различаются)")
   @ApiResponse(responseCode = "429", description = "ADMIN_LOGIN_RATE_LIMITED, OTP_COOLDOWN, OTP_BLOCKED; retryAfter")
   public SendOtpResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest http) {
      return auth.login(request, http.getRemoteAddr());
   }

   @PostMapping("/verify")
   @Operation(summary = "Шаг 2: код из SMS", description = "Ставит cookie admin_refresh и возвращает access-токен и профиль")
   @ApiResponse(responseCode = "400", description = "OTP_INVALID (attemptsLeft), OTP_EXPIRED")
   public AdminSessionDto verify(@Valid @RequestBody AdminVerifyRequest request, HttpServletResponse response) {
      return withCookie(auth.verify(request), response);
   }

   @PostMapping("/refresh")
   @Operation(summary = "Новый access-токен по cookie", description = "Cookie обновляется (старый refresh гасится)")
   @ApiResponse(responseCode = "401", description = "REFRESH_TOKEN_INVALID — войти заново")
   public AdminSessionDto refresh(@CookieValue(name = COOKIE, required = false) String refreshToken,
                                  HttpServletResponse response) {
      return withCookie(auth.refresh(refreshToken), response);
   }

   @PostMapping("/logout")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Выйти", description = "Гасит сессию из cookie и стирает cookie")
   public void logout(@CookieValue(name = COOKIE, required = false) String refreshToken,
                      HttpServletResponse response) {
      auth.logout(refreshToken);
      response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
   }

   @PostMapping("/password/code")
   @Operation(summary = "Код для пароля", description = "Первый вход или забытый пароль. Ответ одинаковый для любого "
         + "номера; SMS приходит только активному сотруднику")
   public SendOtpResponse passwordCode(@Valid @RequestBody AdminPasswordCodeRequest request, HttpServletRequest http) {
      return auth.passwordCode(request.phone(), http.getRemoteAddr());
   }

   @PostMapping("/password")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Задать пароль по коду", description = "От 10 символов, буква и цифра. Закрывает все сессии "
         + "админки этого сотрудника — дальше обычный вход")
   @ApiResponse(responseCode = "400", description = "OTP_INVALID, OTP_EXPIRED, VALIDATION_ERROR")
   public void setPassword(@Valid @RequestBody AdminSetPasswordRequest request) {
      auth.setPassword(request);
   }

   private AdminSessionDto withCookie(AdminAuthService.Session session, HttpServletResponse response) {
      response.addHeader(HttpHeaders.SET_COOKIE,
            cookie(session.refreshToken(), auth.refreshTtl().toSeconds()).toString());
      return session.body();
   }

   private ResponseCookie cookie(String value, long maxAgeSeconds) {
      return ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(config.cookieSecure())
            .sameSite(config.cookieSameSite())
            .path(COOKIE_PATH)
            .maxAge(maxAgeSeconds)
            .build();
   }
}
