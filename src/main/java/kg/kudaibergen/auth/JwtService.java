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
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.springframework.stereotype.Service;

/** Выпуск и разбор JWT. access — 15 минут, refresh — 30 дней. */
@Service
public class JwtService {

   private static final String CLAIM_TYPE = "typ";
   private static final String CLAIM_ROLE = "role";
   private static final String CLAIM_PHONE = "phone";
   private static final String TYPE_ACCESS = "access";
   private static final String TYPE_REFRESH = "refresh";

   private final SecretKey key;
   private final AppProperties.Jwt config;

   public JwtService(AppProperties properties) {
      this.config = properties.jwt();
      this.key = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
   }

   public String generateAccessToken(User user) {
      return build(user, TYPE_ACCESS, config.accessTtl().toSeconds());
   }

   public String generateRefreshToken(User user) {
      return build(user, TYPE_REFRESH, config.refreshTtl().toSeconds());
   }

   public long accessTtlSeconds() {
      return config.accessTtl().toSeconds();
   }

   /** Разбирает access-токен. Бросает JwtException, если подпись/срок/тип не те. */
   public ParsedToken parseAccessToken(String token) {
      return parse(token, TYPE_ACCESS);
   }

   public ParsedToken parseRefreshToken(String token) {
      return parse(token, TYPE_REFRESH);
   }

   private ParsedToken parse(String token, String expectedType) {
      Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
      if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
         throw new JwtException("Неверный тип токена");
      }
      return new ParsedToken(Long.valueOf(claims.getSubject()),
            claims.get(CLAIM_PHONE, String.class),
            UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)));
   }

   private String build(User user, String type, long ttlSeconds) {
      Instant now = Instant.now();
      return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim(CLAIM_PHONE, user.getPhone())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_TYPE, type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact();
   }

   public record ParsedToken(Long userId, String phone, UserRole role) {
   }
}
