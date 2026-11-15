package ro.usv.rrm.common.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ro.usv.rrm.auth.JwtAuthenticationFilter;
import ro.usv.rrm.auth.SecurityErrorHandler;
import ro.usv.rrm.auth.SessionService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configurația de securitate.
 *
 * <p>API-ul este stateless: fiecare cerere se autentifică prin {@link JwtAuthenticationFilter}
 * (token JWT + sesiunea din tabela {@code sesiune}). Toate endpoint-urile cer autentificare, cu
 * excepția celor marcate {@code security: []} în contract și implementate deja (login). Erorile
 * 401/403 au formatul JSON comun.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final RrmProperties properties;

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SessionService sessionService, JsonMapper jsonMapper) throws Exception {
    RequestMatcher publicEndpoints = publicEndpoints();
    SecurityErrorHandler errorHandler = new SecurityErrorHandler(jsonMapper);
    return http
        // API stateless, fără sesiuni pe server și fără formular de login: CSRF nu se aplică.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .addFilterBefore(
            new JwtAuthenticationFilter(sessionService, errorHandler, publicEndpoints),
            UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
        // TODO: TO-33 - restricționarea pe roluri (RBAC cu scop).
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(publicEndpoints)
                .permitAll()
                // Pagina de eroare a Spring Boot, ca erorile să nu fie mascate de un 401.
                .requestMatchers("/error")
                .permitAll()
                .anyRequest()
                .authenticated())
        .build();
  }

  /**
   * Endpoint-urile accesibile fără token. Când se implementează 2FA (TO-32) și SSO (TO-35), se
   * adaugă aici și {@code /auth/2fa/**} și {@code /auth/sso/callback}.
   */
  private RequestMatcher publicEndpoints() {
    return PathPatternRequestMatcher.withDefaults()
        .matcher(HttpMethod.POST, properties.apiPrefix() + "/auth/login");
  }

  /** Hash-urile parolelor din tabela {@code utilizator} sunt BCrypt (cost 10). */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Fără utilizatori în memorie: dezactivează parola generată automat de Spring Boot. Utilizatorii
   * reali vin din baza de date, odată cu autentificarea (TO-1).
   */
  @Bean
  UserDetailsService userDetailsService() {
    return new InMemoryUserDetailsManager();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.cors().allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
