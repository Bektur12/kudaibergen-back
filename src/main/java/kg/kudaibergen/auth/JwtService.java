package kg.kudaibergen.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.entity.AdminRole;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Service;

/** Access-токен JWT (HS256, 15 минут). Refresh — непрозрачный, см. RefreshTokenService. */
@Service
public class JwtService {

   private static final String CLAIM_TYPE = "typ";
   private static final String CLAIM_PHONE = "phone";
   private static final String CLAIM_ADMIN = "adm";
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
            .claim(CLAIM_ADMIN, user.getAdminRole() == null ? null : user.getAdminRole().name())
            .claim(CLAIM_TYPE, TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(config.accessTtl())))
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
      return new AuthPrincipal(Long.valueOf(claims.getSubject()), claims.get(CLAIM_PHONE, String.class),
            adminRole(claims.get(CLAIM_ADMIN, String.class)));
   }

   private static AdminRole adminRole(String value) {
      return value == null ? null : AdminRole.valueOf(value);
   }
}
