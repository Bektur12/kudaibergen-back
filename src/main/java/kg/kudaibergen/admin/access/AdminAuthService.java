package kg.kudaibergen.admin.access;

import java.time.Duration;
import java.util.Optional;

import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminLoginRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminSetPasswordRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminVerifyRequest;
import kg.kudaibergen.admin.access.dto.AdminMeDto;
import kg.kudaibergen.admin.access.dto.AdminSessionDto;
import kg.kudaibergen.admin.audit.AuditLog;
import kg.kudaibergen.auth.JwtService;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.auth.otp.OtpPurpose;
import kg.kudaibergen.auth.otp.OtpService;
import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.sms.SmsTexts;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.auth.token.TokenAudience;
import kg.kudaibergen.common.config.AdminProperties;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.UnauthorizedException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.common.security.AdminGrants.AdminGrant;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход в веб-админку: телефон + пароль → SMS-код → access-токен (audience admin, 15 мин) и refresh в cookie.
 * Код ADMIN_LOGIN выдаётся только после верного пароля, поэтому верный код = оба фактора пройдены.
 * Ответы не различают «нет такого сотрудника», «не задан пароль» и «неверный пароль».
 */
@Service
public class AdminAuthService {

   private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

   private final AdminMemberRepository members;
   private final UserRepository users;
   private final AdminAccess access;
   private final AdminProfiles profiles;
   private final OtpService otp;
   private final SmsProvider sms;
   private final JwtService jwt;
   private final RefreshTokenService refreshTokens;
   private final RateLimiter rateLimiter;
   private final AuditLog audit;
   private final AdminProperties config;
   private final boolean exposeCode;
   private final PasswordEncoder passwords = new BCryptPasswordEncoder();
   /** Сравнение с ним, когда сотрудника нет: время ответа не выдаёт, есть ли такой номер. */
   private final String dummyHash = passwords.encode("no-such-admin-password-1");

   public AdminAuthService(AdminMemberRepository members, UserRepository users, AdminAccess access,
                           AdminProfiles profiles, OtpService otp, SmsProvider sms, JwtService jwt,
                           RefreshTokenService refreshTokens, RateLimiter rateLimiter, AuditLog audit,
                           AdminProperties config, AppProperties properties) {
      this.members = members;
      this.users = users;
      this.access = access;
      this.profiles = profiles;
      this.otp = otp;
      this.sms = sms;
      this.jwt = jwt;
      this.refreshTokens = refreshTokens;
      this.rateLimiter = rateLimiter;
      this.audit = audit;
      this.config = config;
      this.exposeCode = properties.otp().exposeCode();
   }

   /** Шаг 1. Верный пароль — SMS-код на номер сотрудника. */
   public SendOtpResponse login(AdminLoginRequest request, String clientIp) {
      checkFailures(request.phone(), clientIp);
      Optional<User> user = users.findByPhone(request.phone());
      Optional<AdminMember> member = user.flatMap(found -> members.findById(found.getId()));
      String hash = member.filter(AdminMember::hasPassword).map(AdminMember::getPasswordHash).orElse(dummyHash);
      boolean matches = passwords.matches(request.password(), hash);
      if (!matches || member.isEmpty() || !member.get().hasPassword()
            || access.active(member.get().getUserId()).isEmpty()) {
         log.info("Админка: неудачный вход для {}", request.phone());
         countFailure(request.phone(), clientIp);
         throw badCredentials();
      }
      String code = otp.issue(OtpPurpose.ADMIN_LOGIN, request.phone(), clientIp);
      sms.send(request.phone(), SmsTexts.adminLoginCode(code, user.get().getLang()));
      return otpResponse(code);
   }

   /** Шаг 2. Код верный и сотрудник всё ещё активен — сессия. */
   @Transactional
   public Session verify(AdminVerifyRequest request) {
      otp.verify(OtpPurpose.ADMIN_LOGIN, request.phone(), request.code());
      User user = users.findByPhone(request.phone()).orElseThrow(AdminAuthService::badCredentials);
      AdminMember member = members.findById(user.getId()).orElseThrow(AdminAuthService::badCredentials);
      AdminGrant grant = access.active(user.getId()).orElseThrow(AdminAuthService::badCredentials);
      member.loggedIn();
      audit.record(user.getId(), "ADMIN_LOGIN", "ADMIN", user.getId(), null, null, null);
      return session(member, user, grant);
   }

   @Transactional(noRollbackFor = UnauthorizedException.class)
   public Session refresh(String refreshToken) {
      if (refreshToken == null || refreshToken.isBlank()) {
         throw sessionExpired();
      }
      Long userId = refreshTokens.consume(refreshToken, TokenAudience.ADMIN);
      AdminGrant grant = access.active(userId).orElseThrow(AdminAuthService::sessionExpired);
      AdminMember member = members.findById(userId).orElseThrow(AdminAuthService::sessionExpired);
      User user = users.findById(userId).orElseThrow(AdminAuthService::sessionExpired);
      return session(member, user, grant);
   }

   public void logout(String refreshToken) {
      if (refreshToken != null && !refreshToken.isBlank()) {
         refreshTokens.revoke(refreshToken, TokenAudience.ADMIN);
      }
   }

   /** Код для пароля. Ответ одинаковый, есть такой сотрудник или нет; SMS — только активному сотруднику. */
   public SendOtpResponse passwordCode(String phone, String clientIp) {
      limit(phone, clientIp);
      Optional<User> user = users.findByPhone(phone)
            .filter(found -> members.existsById(found.getId()) && access.active(found.getId()).isPresent());
      if (user.isEmpty()) {
         log.info("Админка: код пароля запрошен для номера без доступа {}", phone);
         return otpResponse(null);
      }
      String code = otp.issue(OtpPurpose.ADMIN_PASSWORD, phone, clientIp);
      sms.send(phone, SmsTexts.adminPasswordCode(code, user.get().getLang()));
      return otpResponse(code);
   }

   /** Новый пароль по коду из SMS. Все сессии админки этого сотрудника закрываются. */
   @Transactional
   public void setPassword(AdminSetPasswordRequest request) {
      otp.verify(OtpPurpose.ADMIN_PASSWORD, request.phone(), request.code());
      User user = users.findByPhone(request.phone()).orElseThrow(AdminAuthService::badCredentials);
      AdminMember member = members.findById(user.getId())
            .filter(found -> access.active(found.getUserId()).isPresent())
            .orElseThrow(AdminAuthService::badCredentials);
      member.changePassword(passwords.encode(request.password()));
      refreshTokens.revokeAll(user.getId(), TokenAudience.ADMIN);
      audit.record(user.getId(), "ADMIN_PASSWORD_SET", "ADMIN", user.getId(), null, null, null);
   }

   public Duration refreshTtl() {
      return config.refreshTtl();
   }

   private Session session(AdminMember member, User user, AdminGrant grant) {
      String access = jwt.generateAdminToken(user.getId(), user.getPhone(), grant.role(), grant.permissions(),
            config.accessTtl());
      String refresh = refreshTokens.issue(user.getId(), TokenAudience.ADMIN, config.refreshTtl());
      AdminMeDto me = profiles.of(member, user, grant.permissions());
      return new Session(new AdminSessionDto(access, config.accessTtl().toSeconds(), me), refresh);
   }

   /**
    * Лимит входа считает только неверные пароли: частые удачные входы его не тратят. Повторную SMS сдерживает
    * OTP_COOLDOWN — на него фронт переходит к вводу уже отправленного кода.
    */
   private void checkFailures(String phone, String clientIp) {
      rateLimiter.check("admin-login-fail:phone:" + phone, config.loginLimit(),
            "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      if (clientIp != null) {
         rateLimiter.check("admin-login-fail:ip:" + clientIp, config.loginLimit() * 3,
               "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      }
   }

   private void countFailure(String phone, String clientIp) {
      rateLimiter.hit("admin-login-fail:phone:" + phone, config.loginLimit(), config.loginWindow(),
            "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      if (clientIp != null) {
         rateLimiter.hit("admin-login-fail:ip:" + clientIp, config.loginLimit() * 3, config.loginWindow(),
               "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      }
   }

   /** Код для пароля шлёт SMS — тут считается каждый запрос. */
   private void limit(String phone, String clientIp) {
      rateLimiter.hit("admin-login:phone:" + phone, config.loginLimit(), config.loginWindow(),
            "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      if (clientIp != null) {
         rateLimiter.hit("admin-login:ip:" + clientIp, config.loginLimit() * 3, config.loginWindow(),
               "ADMIN_LOGIN_RATE_LIMITED", "Слишком много попыток входа, попробуйте позже");
      }
   }

   private SendOtpResponse otpResponse(String code) {
      return new SendOtpResponse(otp.codeTtl().toSeconds(), otp.resendInterval().toSeconds(),
            exposeCode ? code : null);
   }

   private static UnauthorizedException badCredentials() {
      return new UnauthorizedException("ADMIN_BAD_CREDENTIALS", "Неверный телефон или пароль");
   }

   private static UnauthorizedException sessionExpired() {
      return new UnauthorizedException("REFRESH_TOKEN_INVALID", "Сессия истекла, войдите заново");
   }

   /** Тело ответа и refresh-токен для cookie. */
   public record Session(AdminSessionDto body, String refreshToken) {
   }
}
