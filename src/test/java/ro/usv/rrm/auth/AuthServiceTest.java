package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.auth.JwtService.IssuedToken;
import ro.usv.rrm.auth.dto.LoginRequest;
import ro.usv.rrm.auth.dto.LoginResponse;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.user.User;
import ro.usv.rrm.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
  private static final String EMAIL = "andrei.popescu@student.usv.ro";
  private static final String PASSWORD = "parola123";

  /** Cost minim, ca testele să fie rapide; în aplicație costul este 10. */
  private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

  @Mock private UserRepository userRepository;
  @Mock private UserSessionRepository sessionRepository;
  @Mock private JwtService jwtService;

  private AuthService authService;
  private User user;

  @BeforeEach
  void setUp() {
    RrmProperties properties =
        new RrmProperties(
            "/api/v1",
            new RrmProperties.Cors(List.of()),
            new RrmProperties.Auth(
                "nefolosit", Duration.ofHours(8), Duration.ofMinutes(60), Duration.ofMinutes(5)));
    authService =
        new AuthService(
            userRepository,
            sessionRepository,
            ENCODER,
            jwtService,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    user = new User("Andrei Popescu", EMAIL, ENCODER.encode(PASSWORD));
    ReflectionTestUtils.setField(user, "id", 6L);
  }

  @Test
  void login_correctPassword_savesSessionAndReturnsToken() {
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));
    given(jwtService.issueAccessToken(eq(6L), any()))
        .willReturn(new IssuedToken("jwt", Duration.ofHours(8)));

    LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD, null));

    assertThat(response.twoFactorRequired()).isFalse();
    assertThat(response.challengeId()).isNull();
    assertThat(response.tokens().accessToken()).isEqualTo("jwt");
    assertThat(response.tokens().expiresIn()).isEqualTo(8 * 3600);

    ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
    then(sessionRepository).should().save(session.capture());
    assertThat(session.getValue().getUser()).isSameAs(user);
    assertThat(session.getValue().getLastActivityAt()).isEqualTo(NOW);
    assertThat(session.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    assertThat(session.getValue().isRevoked()).isFalse();
    // Tokenul conține jti-ul sesiunii salvate, ca TO-31 să o poată găsi și revoca.
    then(jwtService).should().issueAccessToken(6L, session.getValue().getTokenId());
  }

  @Test
  void login_emailWithDifferentCaseAndSpaces_findsUser() {
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));
    given(jwtService.issueAccessToken(eq(6L), any()))
        .willReturn(new IssuedToken("jwt", Duration.ofHours(8)));

    LoginResponse response =
        authService.login(new LoginRequest("  Andrei.Popescu@Student.USV.ro ", PASSWORD, null));

    assertThat(response.tokens()).isNotNull();
  }

  @Test
  void login_wrongPassword_throwsUnauthorizedWithoutSession() {
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

    assertUnauthorized(
        () -> authService.login(new LoginRequest(EMAIL, "gresita", null)), "CREDENTIALE_INVALIDE");
    then(sessionRepository).should(never()).save(any());
  }

  @Test
  void login_unknownEmail_throwsSameErrorAsWrongPassword() {
    given(userRepository.findByEmailIgnoreCase("nimeni@usv.ro")).willReturn(Optional.empty());

    assertUnauthorized(
        () -> authService.login(new LoginRequest("nimeni@usv.ro", PASSWORD, null)),
        "CREDENTIALE_INVALIDE");
    then(sessionRepository).should(never()).save(any());
  }

  @Test
  void login_inactiveAccount_throwsUnauthorized() {
    user.setActive(false);
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

    assertUnauthorized(
        () -> authService.login(new LoginRequest(EMAIL, PASSWORD, null)), "CONT_DEZACTIVAT");
    then(sessionRepository).should(never()).save(any());
  }

  @Test
  void login_twoFactorEnabled_returnsChallengeWithoutSessionOrToken() {
    user.setTwoFactorEnabled(true);
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));
    given(jwtService.issueTwoFactorChallenge(6L)).willReturn("challenge");

    LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD, null));

    assertThat(response.twoFactorRequired()).isTrue();
    assertThat(response.challengeId()).isEqualTo("challenge");
    assertThat(response.tokens()).isNull();
    then(sessionRepository).should(never()).save(any());
    then(jwtService).should(never()).issueAccessToken(anyLong(), any());
  }

  @Test
  void login_twoFactorEnabledAndWrongPassword_throwsUnauthorizedWithoutChallenge() {
    user.setTwoFactorEnabled(true);
    given(userRepository.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(user));

    assertUnauthorized(
        () -> authService.login(new LoginRequest(EMAIL, "gresita", null)), "CREDENTIALE_INVALIDE");
    then(jwtService).should(never()).issueTwoFactorChallenge(anyLong());
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
