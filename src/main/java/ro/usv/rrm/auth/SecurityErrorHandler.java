package ro.usv.rrm.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import ro.usv.rrm.common.error.ApiError;
import tools.jackson.databind.json.JsonMapper;

/**
 * Erorile de securitate (401, 403) în același format JSON ca restul API-ului ({@link ApiError}).
 * Sunt necesare aici pentru că apar în filtre, înainte de controllere și de {@code
 * GlobalExceptionHandler}.
 */
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final JsonMapper jsonMapper;

  /** 401: cerere fără token către un endpoint protejat. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    write(
        response,
        HttpStatus.UNAUTHORIZED,
        ApiError.of("NEAUTENTIFICAT", "Autentifică-te pentru a continua."));
  }

  /** 403: utilizator autentificat, dar fără rolul necesar (folosit de RBAC, TO-33). */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    write(
        response,
        HttpStatus.FORBIDDEN,
        ApiError.of("ACCES_INTERZIS", "Nu ai drepturi pentru această operație."));
  }

  void write(HttpServletResponse response, HttpStatus status, ApiError error) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), error);
  }
}
