package kg.kudaibergen.auth;

import java.time.Duration;
import java.util.Optional;

import kg.kudaibergen.auth.dto.SendOtpRequest;
import kg.kudaibergen.auth.dto.SendOtpResponse;
import kg.kudaibergen.auth.dto.TokenResponse;
import kg.kudaibergen.auth.dto.VerifyOtpRequest;
import kg.kudaibergen.auth.otp.OtpPurpose;
import kg.kudaibergen.auth.otp.OtpService;
import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.user.MeView;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.user.DeviceService;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

   private static final String PHONE = "+996555123456";

   private OtpService otp;
   private SmsProvider sms;
   private UserService userService;
   private UserRepository users;
   private DeviceService devices;
   private JwtService jwt;
   private RefreshTokenService refreshTokens;

   @BeforeEach
   void setUp() {
      otp = mock(OtpService.class);
      sms = mock(SmsProvider.class);
      userService = mock(UserService.class);
      users = mock(UserRepository.class);
      devices = mock(DeviceService.class);
      jwt = mock(JwtService.class);
      refreshTokens = mock(RefreshTokenService.class);
      when(otp.codeTtl()).thenReturn(Duration.ofMinutes(2));
      when(otp.resendInterval()).thenReturn(Duration.ofSeconds(42));
      when(jwt.generateAccessToken(any())).thenReturn("access");
      when(refreshTokens.issue(any())).thenReturn("refresh");
   }

   @Test
   void кодУходитПоSmsНаЯзыкеПользователяИНеСветитсяВПроде() {
      when(otp.issue(OtpPurpose.LOGIN, PHONE, "1.2.3.4")).thenReturn("4812");
      when(users.findByPhone(PHONE)).thenReturn(Optional.of(user(1L, Lang.KG, false)));

      SendOtpResponse response = service(false).sendOtp(new SendOtpRequest(PHONE, Lang.RU), "1.2.3.4");

      assertThat(response.expiresIn()).isEqualTo(120);
      assertThat(response.resendIn()).isEqualTo(42);
      assertThat(response.debugCode()).isNull();
      verify(sms).send(eq(PHONE), contains("кирүү коду 4812"));
   }

   @Test
   void новыйПользовательПолучаетIsNewUser() {
      when(userService.findOrCreate(PHONE, Lang.KG)).thenReturn(user(5L, Lang.KG, false));

      TokenResponse tokens = service(true).verifyOtp(new VerifyOtpRequest(PHONE, "4812", Lang.KG));

      assertThat(tokens.isNewUser()).isTrue();
      assertThat(tokens.accessToken()).isEqualTo("access");
      assertThat(tokens.refreshToken()).isEqualTo("refresh");
      assertThat(tokens.user().lang()).isEqualTo(Lang.KG);
   }

   @Test
   void пользовательСВыбраннойРольюНеНовый() {
      User user = user(5L, Lang.RU, false);
      user.switchRole(UserRole.BUYER);
      when(userService.findOrCreate(PHONE, Lang.RU)).thenReturn(user);

      assertThat(service(true).verifyOtp(new VerifyOtpRequest(PHONE, "4812", null)).isNewUser()).isFalse();
   }

   @Test
   void неверныйКодНеСоздаётПользователя() {
      doThrow(new BadRequestException("OTP_INVALID", "Неверный код")).when(otp).verify(OtpPurpose.LOGIN, PHONE, "0000");

      assertThatThrownBy(() -> service(true).verifyOtp(new VerifyOtpRequest(PHONE, "0000", null)))
            .isInstanceOf(BadRequestException.class);
      verify(userService, never()).findOrCreate(any(), any());
   }

   @Test
   void заблокированныйНеВходит() {
      when(userService.findOrCreate(PHONE, Lang.RU)).thenReturn(user(5L, Lang.RU, true));

      assertThatThrownBy(() -> service(true).verifyOtp(new VerifyOtpRequest(PHONE, "4812", null)))
            .isInstanceOf(ForbiddenException.class)
            .extracting("code").isEqualTo("USER_BLOCKED");
      verify(refreshTokens, never()).issue(any());
   }

   @Test
   void входОтменяетЗапрошенноеУдаление() {
      User user = user(5L, Lang.RU, false);
      user.requestDeletion();
      when(userService.findOrCreate(PHONE, Lang.RU)).thenReturn(user);

      service(true).verifyOtp(new VerifyOtpRequest(PHONE, "4812", null));

      assertThat(user.getDeletionRequestedAt()).isNull();
   }

   @Test
   void подтверждениеУдаленияЗакрываетСессииИУстройства() {
      when(userService.getRequired(5L)).thenReturn(user(5L, Lang.RU, false));

      var response = service(true).confirmDeletion(5L, "4812");

      verify(otp).verify(OtpPurpose.DELETE_ACCOUNT, PHONE, "4812");
      verify(refreshTokens).revokeAll(5L);
      verify(devices).removeAllOf(5L);
      assertThat(response.purgeAt()).isAfter(java.time.Instant.now().plus(Duration.ofDays(29)));
   }

   private AuthService service(boolean exposeCode) {
      AppProperties properties = new AppProperties(null, new AppProperties.Otp(Duration.ofMinutes(2),
            Duration.ofSeconds(42), 5, Duration.ofMinutes(15), 5, 20, Duration.ofHours(1), "secret", exposeCode, null), null, null, null, null, null, null);
      return new AuthService(otp, sms, userService, users, devices, jwt, refreshTokens, new MeView(mock(MediaService.class), userId -> Optional.empty(), userId -> Optional.empty()),
            properties);
   }

   private static User user(Long id, Lang lang, boolean blocked) {
      User user = new User(PHONE, lang);
      ReflectionTestUtils.setField(user, "id", id);
      user.setBlocked(blocked);
      return user;
   }
}
