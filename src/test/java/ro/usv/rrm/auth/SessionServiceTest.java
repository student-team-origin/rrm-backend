package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.auth.JwtService.AccessTokenClaims;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.user.User;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final Instant LOGIN_TIME = Instant.parse("2026-11-15T10:00:00Z");
  private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(60);
  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");
  private static final String TOKEN = "token";

  @Mock private JwtService jwtService;
  @Mock private UserSessionRepository sessionRepository;

  private User user;
  private UserSession session;

  @BeforeEach
  void setUp() {
    user = new User("Andrei Popescu", "andrei.popescu@student.usv.ro", "hash");
    ReflectionTestUtils.setField(user, "id", 6L);
    session = new UserSession(user, SESSION_ID, LOGIN_TIME, LOGIN_TIME.plus(IDLE_TIMEOUT));
  }

  @Test
  void authenticate_activeSession_returnsUserAndExtendsExpiration() {
    Instant now = LOGIN_TIME.plus(Duration.ofMinutes(59));
    givenValidTokenAndSession();

    AuthenticatedUser result = sessionServiceAt(now).authenticate(TOKEN);

    assertThat(result).isEqualTo(new AuthenticatedUser(6, SESSION_ID));
    assertThat(session.getLastActivityAt()).isEqualTo(now);
    assertThat(session.getExpiresAt()).isEqualTo(now.plus(IDLE_TIMEOUT));
  }

  @Test
  void authenticate_exactly60MinutesWithoutActivity_throwsSessionExpired() {
    givenValidTokenAndSession();

    assertUnauthorized(
        () -> sessionServiceAt(LOGIN_TIME.plus(IDLE_TIMEOUT)).authenticate(TOKEN),
        "SESIUNE_EXPIRATA");
  }

  @Test
  void authenticate_activityResetsInactivityTimer() {
    givenValidTokenAndSession();
    // Activitate la 50 de minute: sesiunea expiră abia la 50 + 60 de minute de la login.
    sessionServiceAt(LOGIN_TIME.plus(Duration.ofMinutes(50))).authenticate(TOKEN);

    AuthenticatedUser result =
        sessionServiceAt(LOGIN_TIME.plus(Duration.ofMinutes(100))).authenticate(TOKEN);

    assertThat(result.userId()).isEqualTo(6);
  }

  @Test
  void authenticate_revokedSession_throwsSessionExpired() {
    session.setRevoked(true);
    givenValidTokenAndSession();

    assertUnauthorized(() -> sessionServiceAt(LOGIN_TIME).authenticate(TOKEN), "SESIUNE_EXPIRATA");
  }

  @Test
  void authenticate_sessionNotFound_throwsSessionExpired() {
    given(jwtService.parseAccessToken(TOKEN)).willReturn(new AccessTokenClaims(6, SESSION_ID));
    given(sessionRepository.findByTokenId(SESSION_ID)).willReturn(Optional.empty());

    assertUnauthorized(() -> sessionServiceAt(LOGIN_TIME).authenticate(TOKEN), "SESIUNE_EXPIRATA");
  }

  @Test
  void authenticate_sessionOfAnotherUser_throwsSessionExpired() {
    given(jwtService.parseAccessToken(TOKEN)).willReturn(new AccessTokenClaims(7, SESSION_ID));
    given(sessionRepository.findByTokenId(SESSION_ID)).willReturn(Optional.of(session));

    assertUnauthorized(() -> sessionServiceAt(LOGIN_TIME).authenticate(TOKEN), "SESIUNE_EXPIRATA");
  }

  @Test
  void authenticate_deactivatedUser_throwsUnauthorized() {
    user.setActive(false);
    givenValidTokenAndSession();

    assertUnauthorized(() -> sessionServiceAt(LOGIN_TIME).authenticate(TOKEN), "CONT_DEZACTIVAT");
  }

  @Test
  void revoke_existingSession_marksItRevoked() {
    given(sessionRepository.findByTokenId(SESSION_ID)).willReturn(Optional.of(session));

    sessionServiceAt(LOGIN_TIME).revoke(SESSION_ID);

    assertThat(session.isRevoked()).isTrue();
  }

  private void givenValidTokenAndSession() {
    given(jwtService.parseAccessToken(TOKEN)).willReturn(new AccessTokenClaims(6, SESSION_ID));
    given(sessionRepository.findByTokenId(SESSION_ID)).willReturn(Optional.of(session));
  }

  private SessionService sessionServiceAt(Instant now) {
    RrmProperties properties =
        new RrmProperties(
            "/api/v1",
            new RrmProperties.Cors(List.of()),
            new RrmProperties.Auth(
                "nefolosit", Duration.ofHours(8), IDLE_TIMEOUT, Duration.ofMinutes(5)));
    return new SessionService(
        jwtService, sessionRepository, properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static void assertUnauthorized(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(e.getCode()).isEqualTo(code);
            });
  }
}
