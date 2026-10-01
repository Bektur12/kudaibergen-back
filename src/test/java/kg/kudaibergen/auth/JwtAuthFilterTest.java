package kg.kudaibergen.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.security.AdminGrants;
import kg.kudaibergen.common.security.AdminGrants.AdminGrant;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.LastSeenTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

   private final JwtService jwt = new JwtService(new AppProperties(
         new AppProperties.Jwt("filter-secret-filter-secret-filter-32", Duration.ofMinutes(15), Duration.ofDays(30)),
         null, null, null, null, null, null, null));
   private final AdminGrants grants = mock(AdminGrants.class);
   private final JwtAuthFilter filter = new JwtAuthFilter(jwt, mock(LastSeenTracker.class), grants);

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   void правоБерётсяИзБазыАНеИзТокена() throws Exception {
      // в токене было право SELLERS_BLOCK, а сейчас у роли только SELLERS_VIEW
      when(grants.active(7L)).thenReturn(Optional.of(
            new AdminGrant(AdminRole.MARKET_ADMIN, Set.of(AdminPermission.SELLERS_VIEW))));

      Authentication auth = run("/api/v1/admin/shops", adminToken(Set.of(AdminPermission.SELLERS_BLOCK)));

      assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_ADMIN", "SELLERS_VIEW");
      assertThat(((AuthPrincipal) auth.getPrincipal()).can(AdminPermission.SELLERS_BLOCK)).isFalse();
   }

   @Test
   void токенАдминкиВнеАдминкиНеДействует() throws Exception {
      when(grants.active(7L)).thenReturn(Optional.of(new AdminGrant(AdminRole.SUPER_ADMIN, Set.of())));

      assertThat(run("/api/v1/me", adminToken(Set.of()))).isNull();
   }

   @Test
   void отключённыйСотрудникКакБезТокена() throws Exception {
      when(grants.active(7L)).thenReturn(Optional.empty());

      assertThat(run("/api/v1/admin/me", adminToken(Set.of(AdminPermission.USERS_VIEW)))).isNull();
   }

   @Test
   void токенПриложенияДаётТолькоRoleUser() throws Exception {
      kg.kudaibergen.user.entity.User user = new kg.kudaibergen.user.entity.User("+996555000001",
            kg.kudaibergen.user.entity.Lang.RU);
      org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 3L);

      Authentication auth = run("/api/v1/admin/shops", jwt.generateAccessToken(user));

      assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
   }

   private String adminToken(Set<AdminPermission> permissions) {
      return jwt.generateAdminToken(7L, "+996555000007", AdminRole.MARKET_ADMIN, permissions, Duration.ofMinutes(15));
   }

   private Authentication run(String uri, String token) throws Exception {
      SecurityContextHolder.clearContext();
      MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
      request.addHeader("Authorization", "Bearer " + token);
      filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
      return SecurityContextHolder.getContext().getAuthentication();
   }
}
