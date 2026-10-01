package kg.kudaibergen.auth;

import kg.kudaibergen.auth.dto.DeletionResponse;
import kg.kudaibergen.auth.dto.SendOtpRequest;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.auth.dto.TokenResponse;
import kg.kudaibergen.auth.dto.VerifyOtpRequest;
import kg.kudaibergen.auth.otp.OtpPurpose;
import kg.kudaibergen.auth.otp.OtpService;
import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.sms.SmsTexts;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.UnauthorizedException;
import kg.kudaibergen.user.DeviceService;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.MeView;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход по номеру и SMS-коду (экраны 01–02), обмен и отзыв refresh-токенов,
 * удаление аккаунта с подтверждением по SMS (ТЗ, раздел 3).
 */
@Service
public class AuthService {

   private static final Logger log = LoggerFactory.getLogger(AuthService.class);

   private final OtpService otpService;
   private final SmsProvider smsProvider;
   private final UserService userService;
   private final UserRepository users;
   private final DeviceService devices;
   private final JwtService jwtService;
   private final RefreshTokenService refreshTokens;
   private final MeView meView;
   private final boolean exposeCode;

   public AuthService(OtpService otpService, SmsProvider smsProvider, UserService userService,
                      UserRepository users, DeviceService devices, JwtService jwtService,
                      RefreshTokenService refreshTokens, MeView meView, AppProperties properties) {
      this.otpService = otpService;
      this.smsProvider = smsProvider;
      this.userService = userService;
      this.users = users;
      this.devices = devices;
      this.jwtService = jwtService;
      this.refreshTokens = refreshTokens;
      this.meView = meView;
      this.exposeCode = properties.otp().exposeCode();
   }

   public SendOtpResponse sendOtp(SendOtpRequest request, String clientIp) {
      String code = otpService.issue(OtpPurpose.LOGIN, request.phone(), clientIp);
      // язык SMS: у существующего пользователя — его сохранённый, иначе выбранный на экране 01
      Lang lang = users.findByPhone(request.phone()).map(User::getLang).orElse(request.langOrDefault());
      smsProvider.send(request.phone(), SmsTexts.loginCode(code, lang));
      return otpResponse(code);
   }

   /**
    * Код гасится до выдачи токенов; новый номер сразу регистрируется (роль — отдельным шагом, экран 03).
    * Вход в течение 30 дней после запроса на удаление аккаунта отменяет удаление.
    */
   @Transactional
   public TokenResponse verifyOtp(VerifyOtpRequest request) {
      otpService.verify(OtpPurpose.LOGIN, request.phone(), request.code());
      User user = userService.findOrCreate(request.phone(), request.langOrDefault());
      ensureNotBlocked(user);
      if (user.cancelDeletion()) {
         log.info("Пользователь {} вошёл — удаление аккаунта отменено", user.getId());
      }
      return tokens(user);
   }

   @Transactional(noRollbackFor = UnauthorizedException.class)
   public TokenResponse refresh(String refreshToken) {
      Long userId = refreshTokens.consume(refreshToken);
      User user = users.findById(userId)
            .filter(found -> found.getDeletionRequestedAt() == null)
            .orElseThrow(() -> new UnauthorizedException("REFRESH_TOKEN_INVALID", "Сессия истекла, войдите заново"));
      ensureNotBlocked(user);
      return tokens(user);
   }

   public void logout(Long userId, String refreshToken) {
      refreshTokens.revoke(refreshToken, userId);
   }

   /** Шаг 1 удаления аккаунта: код на номер владельца. */
   public SendOtpResponse sendDeletionOtp(Long userId, String clientIp) {
      User user = userService.getRequired(userId);
      String code = otpService.issue(OtpPurpose.DELETE_ACCOUNT, user.getPhone(), clientIp);
      smsProvider.send(user.getPhone(), SmsTexts.deletionCode(code, user.getLang()));
      return otpResponse(code);
   }

   /** Шаг 2: код верный — помечаем аккаунт к удалению и закрываем все сессии и пуши. */
   @Transactional
   public DeletionResponse confirmDeletion(Long userId, String code) {
      User user = userService.getRequired(userId);
      otpService.verify(OtpPurpose.DELETE_ACCOUNT, user.getPhone(), code);
      user.requestDeletion();
      refreshTokens.revokeAll(userId);
      devices.removeAllOf(userId);
      log.info("Пользователь {} запросил удаление аккаунта", userId);
      return new DeletionResponse(user.getDeletionRequestedAt().plus(UserService.DELETION_GRACE));
   }

   private SendOtpResponse otpResponse(String code) {
      return new SendOtpResponse(otpService.codeTtl().toSeconds(), otpService.resendInterval().toSeconds(),
            exposeCode ? code : null);
   }

   private TokenResponse tokens(User user) {
      return new TokenResponse(jwtService.generateAccessToken(user), refreshTokens.issue(user.getId()),
            jwtService.accessTtlSeconds(), !user.isOnboarded(),
            meView.of(user));
   }

   private static void ensureNotBlocked(User user) {
      if (user.isBlocked()) {
         throw new ForbiddenException("USER_BLOCKED", "Аккаунт заблокирован. Обратитесь в поддержку");
      }
   }
}
