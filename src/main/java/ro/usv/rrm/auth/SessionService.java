package ro.usv.rrm.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.JwtService.AccessTokenClaims;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.user.User;

/**
 * Verificarea sesiunii la fiecare cerere și închiderea ei la logout.
 *
 * <p>Tokenul JWT singur nu ajunge: sesiunea din tabela {@code sesiune} trebuie să nu fie revocată
 * și să nu fi expirat din inactivitate. Fiecare cerere validă mută expirarea cu încă {@code
 * rrm.auth.session-idle-timeout} (60 de minute) de la momentul cererii.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

  private final JwtService jwtService;
  private final UserSessionRepository sessionRepository;
  private final RrmProperties properties;
  private final Clock clock;

  /**
   * Verifică tokenul și sesiunea lui, apoi actualizează ultima activitate.
   *
   * @throws ApiException 401 {@code TOKEN_INVALID} pentru un token invalid; 401 {@code
   *     SESIUNE_EXPIRATA} pentru o sesiune închisă la logout sau expirată din inactivitate; 401
   *     {@code CONT_DEZACTIVAT} dacă între timp contul a fost dezactivat
   */
  @Transactional
  public AuthenticatedUser authenticate(String token) {
    AccessTokenClaims claims = jwtService.parseAccessToken(token);
    UserSession session =
        sessionRepository
            .findByTokenId(claims.sessionTokenId())
            .filter(s -> s.getUser().getId() == claims.userId())
            .orElseThrow(SessionService::sessionExpired);
    Instant now = clock.instant();
    if (session.isRevoked() || !session.getExpiresAt().isAfter(now)) {
      throw sessionExpired();
    }
    User user = session.getUser();
    if (!user.isActive()) {
      throw ApiException.unauthorized(
          "CONT_DEZACTIVAT", "Contul este dezactivat. Contactează administratorul.");
    }
    session.setLastActivityAt(now);
    session.setExpiresAt(now.plus(properties.auth().sessionIdleTimeout()));
    return new AuthenticatedUser(user.getId(), session.getTokenId());
  }

  /** Închide sesiunea (logout): tokenul ei nu mai este acceptat, chiar dacă nu a expirat. */
  @Transactional
  public void revoke(UUID sessionTokenId) {
    sessionRepository
        .findByTokenId(sessionTokenId)
        .ifPresent(session -> {
          session.setRevoked(true);
          log.info("Utilizatorul {} s-a deconectat", session.getUser().getId());
        });
  }

  private static ApiException sessionExpired() {
    return ApiException.unauthorized(
        "SESIUNE_EXPIRATA", "Sesiunea a expirat. Autentifică-te din nou.");
  }
}
