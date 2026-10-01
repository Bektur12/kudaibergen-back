package kg.kudaibergen.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * Access-токены JWT (HS256). Refresh — непрозрачный, см. RefreshTokenService.
 * Токен приложения — без audience; токен админки — audience {@value #ADMIN_AUDIENCE}, роль и права сотрудника.
 */
@Service
public class JwtService {

   public static final String ADMIN_AUDIENCE = "admin";

   private static final String CLAIM_TYPE = "typ";
   private static final String CLAIM_PHONE = "phone";
   private static final String CLAIM_ROLE = "role";
   private static final String CLAIM_PERMISSIONS = "perms";
   private static final String TYPE_ACCESS = "access";

   private final SecretKey key;
   private final AppProperties.Jwt config;

   public JwtService(AppProperties properties) {
      this.config = properties.jwt();
      this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
   }

   public String generateAccessToken(User user) {
      Instant now = Instant.now();
      return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim(CLAIM_PHONE, user.getPhone())
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(config.accessTtl())))
            .signWith(key)
            .compact();
   }

   /** Токен админки. Права в нём — для фронта; сервер на каждый запрос берёт текущие из базы. */
   public String generateAdminToken(Long userId, String phone, AdminRole role, Collection<AdminPermission> permissions,
                                    Duration ttl) {
      Instant now = Instant.now();
      return Jwts.builder()
            .subject(String.valueOf(userId))
            .audience().add(ADMIN_AUDIENCE).and()
            .claim(CLAIM_PHONE, phone)
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_PERMISSIONS, permissions.stream().map(Enum::name).sorted().toList())
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
   }

   public long accessTtlSeconds() {
      return config.accessTtl().toSeconds();
   }

   /** Разбирает access-токен. Бросает JwtException, если подпись/срок/тип не те. */
   public AuthPrincipal parseAccessToken(String token) {
      Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
      if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
         throw new JwtException("Неверный тип токена");
      }
      Long userId = Long.valueOf(claims.getSubject());
      String phone = claims.get(CLAIM_PHONE, String.class);
      Set<String> audience = claims.getAudience();
      if (audience == null || !audience.contains(ADMIN_AUDIENCE)) {
         return AuthPrincipal.user(userId, phone);
      }
      return new AuthPrincipal(userId, phone, AdminRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
            permissions(claims.get(CLAIM_PERMISSIONS, List.class)));
   }

   private static Set<AdminPermission> permissions(List<?> names) {
      Set<AdminPermission> result = EnumSet.noneOf(AdminPermission.class);
      if (names != null) {
         for (Object name : names) {
            try {
               result.add(AdminPermission.valueOf(String.valueOf(name)));
            } catch (IllegalArgumentException retired) {
               // право убрали из кода — просто пропускаем
            }
         }
      }
      return result;
   }
}
