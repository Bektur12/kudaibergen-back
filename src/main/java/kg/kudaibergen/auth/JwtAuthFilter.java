package kg.kudaibergen.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.kudaibergen.common.security.AdminGrants;
import kg.kudaibergen.common.security.AdminGrants.AdminGrant;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.LastSeenTracker;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-токены. Токен приложения даёт ROLE_USER. Токен админки действует только на {@value #ADMIN_PATH}**:
 * права сотрудника берутся из базы на каждый запрос (отключили сотрудника — доступ пропал сразу)
 * и становятся authority с именами AdminPermission плюс ROLE_ADMIN.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

   public static final String ADMIN_PATH = "/api/v1/admin/";
   public static final String ROLE_ADMIN = "ROLE_ADMIN";

   private static final String PREFIX = "Bearer ";

   private final JwtService jwtService;
   private final LastSeenTracker lastSeenTracker;
   private final AdminGrants adminGrants;

   public JwtAuthFilter(JwtService jwtService, LastSeenTracker lastSeenTracker, AdminGrants adminGrants) {
      this.jwtService = jwtService;
      this.lastSeenTracker = lastSeenTracker;
      this.adminGrants = adminGrants;
   }

   @Override
   protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                   @NonNull FilterChain chain) throws ServletException, IOException {
      String header = request.getHeader("Authorization");
      if (header != null && header.startsWith(PREFIX)
            && SecurityContextHolder.getContext().getAuthentication() == null) {
         try {
            AuthPrincipal parsed = jwtService.parseAccessToken(header.substring(PREFIX.length()).trim());
            Optional<AuthPrincipal> principal = parsed.isAdmin() ? admin(parsed, request) : Optional.of(parsed);
            principal.ifPresent(found -> {
               var authentication = new UsernamePasswordAuthenticationToken(found, null, authorities(found));
               authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
               SecurityContextHolder.getContext().setAuthentication(authentication);
               if (!found.isAdmin()) {
                  lastSeenTracker.touch(found.userId());
               }
            });
         } catch (Exception invalidToken) {
            SecurityContextHolder.clearContext();
         }
      }
      chain.doFilter(request, response);
   }

   /** Сессия админки: только на её путях и только пока сотрудник активен. */
   private Optional<AuthPrincipal> admin(AuthPrincipal parsed, HttpServletRequest request) {
      if (!request.getRequestURI().startsWith(ADMIN_PATH)) {
         return Optional.empty();
      }
      return adminGrants.active(parsed.userId())
            .map((AdminGrant grant) -> new AuthPrincipal(parsed.userId(), parsed.phone(), grant.role(),
                  grant.permissions()));
   }

   static List<GrantedAuthority> authorities(AuthPrincipal principal) {
      List<GrantedAuthority> authorities = new ArrayList<>();
      if (!principal.isAdmin()) {
         authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
         return authorities;
      }
      authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
      for (AdminPermission permission : principal.permissions()) {
         authorities.add(new SimpleGrantedAuthority(permission.name()));
      }
      return authorities;
   }
}
