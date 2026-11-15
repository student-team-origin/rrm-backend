package ro.usv.rrm.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ro.usv.rrm.common.error.ApiError;
import ro.usv.rrm.common.error.ApiException;

/**
 * Autentifică cererea după header-ul {@code Authorization: Bearer <token>}.
 *
 * <ul>
 *   <li>Fără header: cererea merge mai departe neautentificată; regulile din {@code SecurityConfig}
 *       decid dacă endpoint-ul e public (altfel 401 {@code NEAUTENTIFICAT}).
 *   <li>Cu token valid și sesiune activă: utilizatorul ajunge în {@code SecurityContext}.
 *   <li>Cu token invalid sau sesiune expirată/închisă: răspuns 401 imediat.
 * </ul>
 *
 * <p>Nu este bean Spring: se creează în {@code SecurityConfig}, ca să ruleze doar o dată, în lanțul
 * de securitate, nu și ca filtru servlet separat.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final SessionService sessionService;
  private final SecurityErrorHandler errorHandler;

  /**
   * Endpoint-urile publice (ex. login) ignoră header-ul, ca un token vechi rămas în browser să nu
   * blocheze o autentificare nouă.
   */
  private final RequestMatcher publicEndpoints;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return publicEndpoints.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    try {
      AuthenticatedUser user =
          sessionService.authenticate(header.substring(BEARER_PREFIX.length()).strip());
      // Rolurile (authorities) se adaugă în TO-33.
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    } catch (ApiException e) {
      SecurityContextHolder.clearContext();
      errorHandler.write(response, e.getStatus(), ApiError.of(e.getCode(), e.getMessage()));
      return;
    }
    chain.doFilter(request, response);
  }
}
