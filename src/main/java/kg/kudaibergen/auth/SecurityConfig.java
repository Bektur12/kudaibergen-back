package kg.kudaibergen.auth;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import kg.kudaibergen.common.error.Problems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

   private static final String[] PUBLIC_READ = {
         "/api/v1/market/**",
         "/api/v1/brands/**",
         "/api/v1/models/**",
         // логотипы марок из design/assets/brands, отдаются как статика
         "/assets/**",
         "/api/v1/categories/**",
         "/api/v1/parts/**",
         "/api/v1/shops/**",
         "/api/v1/reviews/tags"
   };

   private final JwtAuthFilter jwtAuthFilter;
   private final ObjectMapper objectMapper;

   public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
      this.jwtAuthFilter = jwtAuthFilter;
      this.objectMapper = objectMapper;
   }

   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                  .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/otp/send",
                        "/api/v1/auth/otp/verify",
                        "/api/v1/auth/refresh").permitAll()
                  .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/actuator/health", "/actuator/health/**", "/error").permitAll()
                  // гость (ТЗ, раздел 2): карта, маршрут, каталог, карточка товара, профиль магазина.
                  // Токен, если он есть, всё равно разбирается — например, для признака «Подходит».
                  .requestMatchers(HttpMethod.GET, PUBLIC_READ).permitAll()
                  .requestMatchers("/api/v1/admin/**").hasAnyRole("MARKET_ADMIN", "SUPERADMIN")
                  .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                  .authenticationEntryPoint((request, response, ex) -> write(response,
                        Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется авторизация")))
                  .accessDeniedHandler((request, response, ex) -> write(response,
                        Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа к ресурсу"))))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
      return http.build();
   }

   @Bean
   public CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration config = new CorsConfiguration();
      // dev-режим: любой origin с локальной машины (фронтенд может слушать любой порт)
      config.setAllowedOriginPatterns(List.of("*"));
      config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
      config.setAllowedHeaders(List.of("*"));
      config.setExposedHeaders(List.of("Retry-After", "ETag"));
      config.setAllowCredentials(true);

      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", config);
      return source;
   }

   private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
      response.setStatus(problem.getStatus());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), problem);
   }
}
