package kg.kudaibergen.auth.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

   private RefreshTokenRepository repository;
   private RefreshTokenService service;

   @BeforeEach
   void setUp() {
      repository = mock(RefreshTokenRepository.class);
      AppProperties properties = new AppProperties(
            new AppProperties.Jwt("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(30)), null, null, null, null, null, null, null);
      service = new RefreshTokenService(repository, properties);
   }

   @Test
   void выпущенныйТокенХранитсяТолькоХэшем() {
      when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      String token = service.issue(7L);

      verify(repository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.getUserId() == 7L
                  && saved.getTokenHash().equals(RefreshTokenService.sha256(token))
                  && !saved.getTokenHash().equals(token)
                  && saved.getExpiresAt().isAfter(Instant.now().plus(Duration.ofDays(29)))));
   }

   @Test
   void действующийТокенГаситсяИВозвращаетВладельца() {
      RefreshToken stored = stored(7L, Instant.now().plusSeconds(60));
      when(repository.findByTokenHash(RefreshTokenService.sha256("t"))).thenReturn(Optional.of(stored));
      when(repository.revoke(eq(stored.getId()), any())).thenReturn(1);

      assertThat(service.consume("t")).isEqualTo(7L);
      verify(repository, never()).revokeAllOfUser(anyLong(), any(TokenAudience.class), any());
   }

   @Test
   void повторноеИспользованиеГаситВсеСессии() {
      RefreshToken stored = stored(7L, Instant.now().plusSeconds(60));
      when(repository.findByTokenHash(RefreshTokenService.sha256("t"))).thenReturn(Optional.of(stored));
      // параллельный обмен уже погасил токен
      when(repository.revoke(eq(stored.getId()), any())).thenReturn(0);

      assertThatThrownBy(() -> service.consume("t"))
            .isInstanceOf(UnauthorizedException.class)
            .extracting("code").isEqualTo("REFRESH_TOKEN_INVALID");
      verify(repository).revokeAllOfUser(eq(7L), eq(TokenAudience.APP), any());
   }

   @Test
   void токенАдминкиНеОбмениваетсяВПриложенииИНаоборот() {
      when(repository.findByTokenHash(RefreshTokenService.sha256("admin")))
            .thenReturn(Optional.of(new RefreshToken(7L, "hash", Instant.now().plusSeconds(60), TokenAudience.ADMIN)));
      when(repository.findByTokenHash(RefreshTokenService.sha256("app")))
            .thenReturn(Optional.of(stored(7L, Instant.now().plusSeconds(60))));

      assertThatThrownBy(() -> service.consume("admin")).isInstanceOf(UnauthorizedException.class);
      assertThatThrownBy(() -> service.consume("app", TokenAudience.ADMIN)).isInstanceOf(UnauthorizedException.class);
      verify(repository, never()).revoke(anyLong(), any());
   }

   @Test
   void просроченныйИНеизвестныйТокеныОтклоняются() {
      when(repository.findByTokenHash(RefreshTokenService.sha256("old")))
            .thenReturn(Optional.of(stored(7L, Instant.now().minusSeconds(1))));
      when(repository.findByTokenHash(RefreshTokenService.sha256("unknown"))).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.consume("old")).isInstanceOf(UnauthorizedException.class);
      assertThatThrownBy(() -> service.consume("unknown")).isInstanceOf(UnauthorizedException.class);
      verify(repository, never()).revoke(anyLong(), any());
   }

   private static RefreshToken stored(Long userId, Instant expiresAt) {
      return new RefreshToken(userId, "hash", expiresAt);
   }
}
