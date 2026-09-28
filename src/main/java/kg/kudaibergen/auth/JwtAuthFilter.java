package kg.kudaibergen.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

   private static final String PREFIX = "Bearer ";

   private final JwtService jwtService;
   private final LastSeenTracker lastSeenTracker;

   public JwtAuthFilter(JwtService jwtService, LastSeenTracker lastSeenTracker) {
      this.jwtService = jwtService;
      this.lastSeenTracker = lastSeenTracker;
   }

   @Override
   protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                   @NonNull FilterChain chain) throws ServletException, IOException {
      String header = request.getHeader("Authorization");
      if (header != null && header.startsWith(PREFIX)
            && SecurityContextHolder.getContext().getAuthentication() == null) {
         try {
            AuthPrincipal principal = jwtService.parseAccessToken(header.substring(PREFIX.length()).trim());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities(principal));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            lastSeenTracker.touch(principal.userId());
         } catch (Exception invalidToken) {
            SecurityContextHolder.clearContext();
         }
      }
      chain.doFilter(request, response);
   }

   private static List<GrantedAuthority> authorities(AuthPrincipal principal) {
      List<GrantedAuthority> authorities = new ArrayList<>();
      authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
      // суперадмин может всё, что админ рынка
      if (principal.isMarketAdmin()) {
         authorities.add(new SimpleGrantedAuthority("ROLE_MARKET_ADMIN"));
      }
      if (principal.isSuperadmin()) {
         authorities.add(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
      }
      return authorities;
   }
}
