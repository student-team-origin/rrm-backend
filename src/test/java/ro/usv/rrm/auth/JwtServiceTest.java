package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ro.usv.rrm.auth.JwtService.AccessTokenClaims;
import ro.usv.rrm.auth.JwtService.IssuedToken;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;

class JwtServiceTest {

  private static final String SECRET = "secret-de-test-cu-cel-putin-32-de-caractere";
  private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");

  private final JwtService jwtService = jwtServiceAt(NOW, SECRET);

  @Test
  void issueAccessToken_thenParse_returnsUserAndSession() {
    IssuedToken token = jwtService.issueAccessToken(6, SESSION_ID);

    assertThat(token.ttl()).isEqualTo(Duration.ofHours(8));
    assertThat(jwtService.parseAccessToken(token.value()))
        .isEqualTo(new AccessTokenClaims(6, SESSION_ID));
  }

  @Test
  void parseAccessToken_afterExpiration_throwsUnauthorized() {
    String token = jwtService.issueAccessToken(6, SESSION_ID).value();
    JwtService later = jwtServiceAt(NOW.plus(Duration.ofHours(8)).plusSeconds(1), SECRET);

    assertUnauthorized(() -> later.parseAccessToken(token));
  }

  @Test
  void parseAccessToken_signedWithAnotherSecret_throwsUnauthorized() {
    String token = jwtService.issueAccessToken(6, SESSION_ID).value();
    JwtService other = jwtServiceAt(NOW, "alt-secret-de-test-cu-cel-putin-32-de-caractere");

    assertUnauthorized(() -> other.parseAccessToken(token));
  }

  @Test
  void parseAccessToken_withTwoFactorChallenge_throwsUnauthorized() {
    String challenge = jwtService.issueTwoFactorChallenge(1);

    assertUnauthorized(() -> jwtService.parseAccessToken(challenge));
  }

  @Test
  void parseAccessToken_malformed_throwsUnauthorized() {
    assertUnauthorized(() -> jwtService.parseAccessToken("nu-este-un-jwt"));
  }

  @Test
  void parseTwoFactorChallenge_withinFiveMinutes_returnsUserId() {
    String challenge = jwtService.issueTwoFactorChallenge(1);
    JwtService fourMinutesLater = jwtServiceAt(NOW.plus(Duration.ofMinutes(4)), SECRET);

    assertThat(fourMinutesLater.parseTwoFactorChallenge(challenge)).isEqualTo(1);
  }

  @Test
  void parseTwoFactorChallenge_afterFiveMinutes_throwsUnauthorized() {
    String challenge = jwtService.issueTwoFactorChallenge(1);
    JwtService sixMinutesLater = jwtServiceAt(NOW.plus(Duration.ofMinutes(6)), SECRET);

    assertUnauthorized(() -> sixMinutesLater.parseTwoFactorChallenge(challenge));
  }

  @Test
  void constructor_shortSecret_failsAtStartup() {
    assertThatThrownBy(() -> jwtServiceAt(NOW, "prea-scurt"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32");
  }

  private static JwtService jwtServiceAt(Instant now, String secret) {
    RrmProperties properties =
        new RrmProperties(
            "/api/v1",
            new RrmProperties.Cors(List.of()),
            new RrmProperties.Auth(
                secret, Duration.ofHours(8), Duration.ofMinutes(60), Duration.ofMinutes(5)));
    return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static void assertUnauthorized(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(e.getCode()).isEqualTo("TOKEN_INVALID");
            });
  }
}
