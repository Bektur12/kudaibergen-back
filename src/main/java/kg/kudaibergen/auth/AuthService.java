package kg.kudaibergen.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

import io.jsonwebtoken.JwtException;
import kg.kudaibergen.auth.dto.RefreshRequest;
import kg.kudaibergen.auth.dto.RegisterRoleRequest;
import kg.kudaibergen.auth.dto.RequestCodeRequest;
import kg.kudaibergen.auth.dto.RequestCodeResponse;
import kg.kudaibergen.auth.dto.TokenResponse;
import kg.kudaibergen.auth.dto.VerifyRequest;
import kg.kudaibergen.auth.entity.SmsCode;
import kg.kudaibergen.auth.sms.SmsSender;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.store.StoreService;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход по SMS: код 4 цифры, живёт 2 минуты, 3 попытки ввода,
 * не чаще одной отправки в минуту на номер.
 */
@Service
public class AuthService {

   private static final Logger log = LoggerFactory.getLogger(AuthService.class);
   private static final SecureRandom RANDOM = new SecureRandom();

   private final SmsCodeRepository smsCodes;
   private final UserRepository users;
   private final SmsSender smsSender;
   private final PasswordEncoder passwordEncoder;
   private final JwtService jwtService;
   private final StoreService storeService;
   private final AppProperties.Sms config;

   public AuthService(SmsCodeRepository smsCodes, UserRepository users, SmsSender smsSender,
                      PasswordEncoder passwordEncoder, JwtService jwtService, StoreService storeService,
                      AppProperties properties) {
      this.smsCodes = smsCodes;
      this.users = users;
      this.smsSender = smsSender;
      this.passwordEncoder = passwordEncoder;
      this.jwtService = jwtService;
      this.storeService = storeService;
      this.config = properties.sms();
   }

   @Transactional
   public RequestCodeResponse requestCode(RequestCodeRequest request) {
      String phone = request.phone();
      Instant now = Instant.now();

      smsCodes.findTopByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
         if (last.getCreatedAt().isAfter(now.minus(config.resendInterval()))) {
            throw new RateLimitException("SMS_TOO_OFTEN",
                  "Новый код можно запросить раз в " + config.resendInterval().toSeconds() + " секунд");
         }
      });

      String code = String.format("%04d", RANDOM.nextInt(10_000));
      smsCodes.save(new SmsCode(phone, passwordEncoder.encode(code), now.plus(config.codeTtl())));
      smsSender.send(phone, "Код входа Kudaibergen: " + code);

      return new RequestCodeResponse(config.codeTtl().toSeconds(), config.exposeCode() ? code : null);
   }

   @Transactional
   public TokenResponse verify(VerifyRequest request) {
      Instant now = Instant.now();
      SmsCode smsCode = smsCodes.findTopByPhoneOrderByCreatedAtDesc(request.phone())
            .orElseThrow(() -> new BadRequestException("CODE_NOT_REQUESTED", "Код не запрашивался"));

      if (smsCode.isUsed()) {
         throw new BadRequestException("CODE_ALREADY_USED", "Код уже использован");
      }
      if (smsCode.isExpired(now)) {
         throw new BadRequestException("CODE_EXPIRED", "Срок действия кода истёк");
      }
      if (smsCode.getAttempts() >= config.maxAttempts()) {
         throw new RateLimitException("CODE_ATTEMPTS_EXCEEDED", "Превышено число попыток, запросите новый код");
      }
      if (!passwordEncoder.matches(request.code(), smsCode.getCodeHash())) {
         smsCode.registerAttempt();
         throw new BadRequestException("INVALID_CODE", "Неверный код", "code");
      }
      smsCode.markUsed();

      Optional<User> existing = users.findByPhone(request.phone());
      User user = existing.orElseGet(() -> {
         log.info("Регистрация нового пользователя {}", request.phone());
         // роль уточняется отдельным шагом /auth/register-role
         return users.save(new User(request.phone(), UserRole.BUYER));
      });
      if (user.isBlocked()) {
         throw new ForbiddenException("USER_BLOCKED", "Пользователь заблокирован");
      }

      boolean isNewUser = user.getName() == null;
      return tokens(user, isNewUser);
   }

   @Transactional(readOnly = true)
   public TokenResponse refresh(RefreshRequest request) {
      JwtService.ParsedToken parsed;
      try {
         parsed = jwtService.parseRefreshToken(request.refreshToken());
      } catch (JwtException invalid) {
         throw new BadRequestException("INVALID_REFRESH_TOKEN", "Refresh-токен недействителен");
      }
      User user = users.findById(parsed.userId())
            .orElseThrow(() -> new BadRequestException("INVALID_REFRESH_TOKEN", "Refresh-токен недействителен"));
      if (user.isBlocked()) {
         throw new ForbiddenException("USER_BLOCKED", "Пользователь заблокирован");
      }
      return tokens(user, false);
   }

   /**
    * Второй шаг регистрации: пользователь выбирает роль и имя.
    * Для продавца сразу заводим магазин — иначе весь кабинет /my-store пустой.
    */
   @Transactional
   public TokenResponse registerRole(Long userId, RegisterRoleRequest request) {
      User user = users.findById(userId)
            .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "Пользователь не найден"));
      if (user.getName() != null) {
         throw new ConflictException("REGISTRATION_ALREADY_COMPLETED", "Регистрация уже завершена");
      }

      user.setRole(request.role());
      user.setName(request.name() == null || request.name().isBlank() ? "Пользователь" : request.name().trim());
      if (request.city() != null && !request.city().isBlank()) {
         user.setCity(request.city().trim());
      }
      if (request.role() == UserRole.SELLER) {
         storeService.createForOwner(user, request.storeName(), request.businessType());
      }
      // роль попала в токен — выдаём новую пару
      return tokens(user, false);
   }

   private TokenResponse tokens(User user, boolean isNewUser) {
      return new TokenResponse(jwtService.generateAccessToken(user), jwtService.generateRefreshToken(user),
            jwtService.accessTtlSeconds(), isNewUser, user.getRole());
   }
}
