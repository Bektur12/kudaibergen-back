package kg.kudaibergen.auth;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import kg.kudaibergen.common.error.ApiErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

   private final JwtAuthFilter jwtAuthFilter;
   private final ObjectMapper objectMapper;

   public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
      this.jwtAuthFilter = jwtAuthFilter;
      this.objectMapper = objectMapper;
   }

   @Bean
   public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
   }

   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                  .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/request-code",
                        "/api/v1/auth/verify",
                        "/api/v1/auth/refresh").permitAll()
                  .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/actuator/health", "/actuator/health/**").permitAll()
                  .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                  // WebSocket handshake: браузер не может выставить Authorization на CONNECT,
                  // токен проверяется внутри STOMP-фрейма — см. ChatWebSocketInterceptor
                  .requestMatchers("/ws/**").permitAll()
                  // весь кабинет продавца — только роль SELLER
                  .requestMatchers("/api/v1/my-store/**").hasRole("SELLER")
                  .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                  .authenticationEntryPoint((request, response, ex) ->
                        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                              new ApiErrorResponse("UNAUTHORIZED", "Требуется авторизация", null)))
                  .accessDeniedHandler((request, response, ex) ->
                        write(response, HttpServletResponse.SC_FORBIDDEN,
                              new ApiErrorResponse("FORBIDDEN", "Нет доступа к ресурсу", null))))
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
      config.setAllowCredentials(true);

      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", config);
      return source;
   }

   private void write(HttpServletResponse response, int status, ApiErrorResponse body) throws java.io.IOException {
      response.setStatus(status);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      objectMapper.writeValue(response.getOutputStream(), body);
   }
}
