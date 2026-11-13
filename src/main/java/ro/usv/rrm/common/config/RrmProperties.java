package ro.usv.rrm.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Setările aplicației din secțiunea {@code rrm} a fișierului {@code application.yml}.
 *
 * @param apiPrefix prefixul tuturor endpoint-urilor REST (ex. {@code /api/v1})
 * @param cors setările CORS pentru frontend
 * @param auth setările de autentificare (JWT, sesiune, 2FA)
 */
@ConfigurationProperties("rrm")
public record RrmProperties(String apiPrefix, Cors cors, Auth auth) {

  /**
   * Setările CORS.
   *
   * @param allowedOrigins originile din care browserul poate apela API-ul
   */
  public record Cors(List<String> allowedOrigins) {}

  /**
   * Setările de autentificare.
   *
   * @param jwtSecret cheia HMAC pentru semnarea tokenurilor, de minimum 32 de octeți
   * @param accessTokenTtl durata maximă a unui token de acces
   * @param sessionIdleTimeout după cât timp fără activitate expiră sesiunea (60 de minute, TO-1)
   * @param twoFactorChallengeTtl cât timp poate fi introdus codul 2FA după login
   */
  public record Auth(
      String jwtSecret,
      Duration accessTokenTtl,
      Duration sessionIdleTimeout,
      Duration twoFactorChallengeTtl) {}
}
