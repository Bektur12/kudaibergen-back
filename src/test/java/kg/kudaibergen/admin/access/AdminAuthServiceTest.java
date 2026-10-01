package kg.kudaibergen.admin.access;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminLoginRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminSetPasswordRequest;
import kg.kudaibergen.admin.access.dto.AdminAuthRequests.AdminVerifyRequest;
import kg.kudaibergen.admin.audit.AuditLog;
import kg.kudaibergen.auth.JwtService;
import kg.kudaibergen.auth.otp.OtpPurpose;
import kg.kudaibergen.auth.otp.OtpService;
import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.auth.token.TokenAudience;
import kg.kudaibergen.common.config.AdminProperties;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.UnauthorizedException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.common.security.AdminGrants.AdminGrant;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceTest {

   private static final String PHONE = "+996555000009";
   private static final String PASSWORD = "correct-horse-7";

   private AdminMemberRepository members;
   private UserRepository users;
   private AdminAccess access;
   private OtpService otp;
   private SmsProvider sms;
   private RefreshTokenService refreshTokens;
   private AdminAuthService service;
   private User user;
   private AdminMember member;

   @BeforeEach
   void setUp() {
      members = mock(AdminMemberRepository.class);
      users = mock(UserRepository.class);
      access = mock(AdminAccess.class);
      otp = mock(OtpService.class);
      sms = mock(SmsProvider.class);
      refreshTokens = mock(RefreshTokenService.class);
      JwtService jwt = mock(JwtService.class);
      AdminProfiles profiles = mock(AdminProfiles.class);
      when(otp.codeTtl()).thenReturn(Duration.ofMinutes(2));
      when(otp.resendInterval()).thenReturn(Duration.ofSeconds(42));
      when(otp.issue(any(), anyString(), any())).thenReturn("4321");
      when(jwt.generateAdminToken(any(), any(), any(), any(), any())).thenReturn("admin-access");
      when(refreshTokens.issue(any(), eq(TokenAudience.ADMIN), any())).thenReturn("admin-refresh");

      user = new User(PHONE, Lang.RU);
      ReflectionTestUtils.setField(user, "id", 9L);
      member = new AdminMember(9L, AdminRole.MARKET_ADMIN, "Айбек Т.", "Администратор рынка", null);
      member.changePassword(new BCryptPasswordEncoder().encode(PASSWORD));
      when(users.findByPhone(PHONE)).thenReturn(Optional.of(user));
      when(users.findById(9L)).thenReturn(Optional.of(user));
      when(members.findById(9L)).thenReturn(Optional.of(member));
      when(members.existsById(9L)).thenReturn(true);
      when(access.active(9L)).thenReturn(Optional.of(
            new AdminGrant(AdminRole.MARKET_ADMIN, Set.of(AdminPermission.SELLERS_VIEW))));

      AdminProperties config = new AdminProperties(List.of("http://localhost:*"), Duration.ofMinutes(15),
            Duration.ofHours(12), true, "Strict", "", "", 10, Duration.ofMinutes(15));
      AppProperties properties = new AppProperties(null, new AppProperties.Otp(Duration.ofMinutes(2),
            Duration.ofSeconds(42), 5, Duration.ofMinutes(15), 5, 20, Duration.ofHours(1), "x", true, ""),
            null, null, null, null, null, null);
      service = new AdminAuthService(members, users, access, profiles, otp, sms, jwt, refreshTokens,
            mock(RateLimiter.class), mock(AuditLog.class), config, properties);
   }

   @Test
   void верныйПарольОтправляетКодАдминки() {
      var sent = service.login(new AdminLoginRequest(PHONE, PASSWORD), "10.0.0.1");

      verify(otp).issue(OtpPurpose.ADMIN_LOGIN, PHONE, "10.0.0.1");
      verify(sms).send(eq(PHONE), contains("4321"));
      assertThat(sent.debugCode()).isEqualTo("4321");
   }

   @Test
   void неверныйПарольНеСотрудникБезПароляИОтключённыйОдинаковы() {
      assertBadCredentials(new AdminLoginRequest(PHONE, "wrong-password-1"));

      when(access.active(9L)).thenReturn(Optional.empty());
      assertBadCredentials(new AdminLoginRequest(PHONE, PASSWORD));

      when(users.findByPhone("+996555000010")).thenReturn(Optional.empty());
      assertBadCredentials(new AdminLoginRequest("+996555000010", PASSWORD));

      AdminMember noPassword = new AdminMember(9L, AdminRole.MARKET_ADMIN, "Айбек Т.", "Администратор рынка", null);
      when(members.findById(9L)).thenReturn(Optional.of(noPassword));
      assertBadCredentials(new AdminLoginRequest(PHONE, PASSWORD));

      verify(otp, never()).issue(any(), anyString(), any());
   }

   @Test
   void кодДаётСессиюАдминкиИОтмечаетВход() {
      var session = service.verify(new AdminVerifyRequest(PHONE, "4321"));

      verify(otp).verify(OtpPurpose.ADMIN_LOGIN, PHONE, "4321");
      assertThat(session.body().accessToken()).isEqualTo("admin-access");
      assertThat(session.body().expiresIn()).isEqualTo(900);
      assertThat(session.refreshToken()).isEqualTo("admin-refresh");
      assertThat(member.getLastLoginAt()).isNotNull();
   }

   @Test
   void кодПароляНеСотрудникуНеОтправляется() {
      when(users.findByPhone("+996555000010")).thenReturn(Optional.empty());

      var sent = service.passwordCode("+996555000010", "10.0.0.1");

      assertThat(sent.expiresIn()).isEqualTo(120);
      verify(otp, never()).issue(any(), anyString(), any());
      verify(sms, never()).send(anyString(), anyString());
   }

   @Test
   void новыйПарольЗакрываетСессииАдминки() {
      service.setPassword(new AdminSetPasswordRequest(PHONE, "1111", "new-password-2026"));

      verify(otp).verify(OtpPurpose.ADMIN_PASSWORD, PHONE, "1111");
      assertThat(new BCryptPasswordEncoder().matches("new-password-2026", member.getPasswordHash())).isTrue();
      verify(refreshTokens).revokeAll(9L, TokenAudience.ADMIN);
   }

   private void assertBadCredentials(AdminLoginRequest request) {
      assertThatThrownBy(() -> service.login(request, "10.0.0.1"))
            .isInstanceOf(UnauthorizedException.class)
            .extracting("code").isEqualTo("ADMIN_BAD_CREDENTIALS");
   }
}
