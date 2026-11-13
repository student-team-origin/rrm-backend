package ro.usv.rrm.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;

/**
 * Emiterea și verificarea tokenurilor JWT (semnate HMAC-SHA, cu JJWT).
 *
 * <p>Există două tipuri de tokenuri, deosebite prin claim-ul {@value #TYPE_CLAIM}:
 *
 * <ul>
 *   <li>tokenul de acces, trimis la fiecare cerere în header-ul {@code Authorization: Bearer};
 *   <li>provocarea 2FA ({@code challengeId}), valabilă câteva minute între login și introducerea
 *       codului. Nu poate fi folosită ca token de acces.
 * </ul>
 */
@Service
public class JwtService {

  static final String TYPE_CLAIM = "tip";
  static final String ACCESS_TYPE = "acces";
  static final String TWO_FACTOR_TYPE = "2fa";

  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;
  private final Duration accessTokenTtl;
  private final Duration twoFactorChallengeTtl;
  private final Clock clock;

  public JwtService(RrmProperties properties, Clock clock) {
    byte[] secret = properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "rrm.auth.jwt-secret (JWT_SECRET) trebuie să aibă minimum 32 de octeți.");
    }
    this.key = Keys.hmacShaKeyFor(secret);
    this.accessTokenTtl = properties.auth().accessTokenTtl();
    this.twoFactorChallengeTtl = properties.auth().twoFactorChallengeTtl();
    this.clock = clock;
  }

  /** Tokenul de acces pentru sesiunea {@code sessionTokenId} a utilizatorului. */
  public IssuedToken issueAccessToken(long userId, UUID sessionTokenId) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(accessTokenTtl);
    String token =
        Jwts.builder()
            .id(sessionTokenId.toString())
            .subject(Long.toString(userId))
            .claim(TYPE_CLAIM, ACCESS_TYPE)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();
    return new IssuedToken(token, accessTokenTtl);
  }

  /** Provocarea 2FA emisă după o parolă corectă, când utilizatorul are 2FA activ. */
  public String issueTwoFactorChallenge(long userId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(Long.toString(userId))
        .claim(TYPE_CLAIM, TWO_FACTOR_TYPE)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(twoFactorChallengeTtl)))
        .signWith(key)
        .compact();
  }

  /**
   * Verifică semnătura, expirarea și tipul unui token de acces.
   *
   * @throws ApiException 401 {@code TOKEN_INVALID} dacă tokenul nu este valid
   */
  public AccessTokenClaims parseAccessToken(String token) {
    Claims claims = parse(token, ACCESS_TYPE);
    return new AccessTokenClaims(
        Long.parseLong(claims.getSubject()), UUID.fromString(claims.getId()));
  }

  /**
   * Verifică o provocare 2FA și întoarce id-ul utilizatorului.
   *
   * @throws ApiException 401 {@code TOKEN_INVALID} dacă provocarea nu este validă sau a expirat
   */
  public long parseTwoFactorChallenge(String challengeId) {
    return Long.parseLong(parse(challengeId, TWO_FACTOR_TYPE).getSubject());
  }

  private Claims parse(String token, String expectedType) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(key)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
        throw new JwtException("Tip de token neașteptat.");
      }
      return claims;
    } catch (JwtException | IllegalArgumentException e) {
      throw ApiException.unauthorized(
          "TOKEN_INVALID", "Sesiunea nu este validă sau a expirat. Autentifică-te din nou.");
    }
  }

  /**
   * Un token emis.
   *
   * @param value tokenul JWT
   * @param ttl durata de valabilitate
   */
  public record IssuedToken(String value, Duration ttl) {}

  /**
   * Datele dintr-un token de acces valid.
   *
   * @param userId id-ul utilizatorului
   * @param sessionTokenId {@code jti}-ul sesiunii din tabela {@code sesiune}
   */
  public record AccessTokenClaims(long userId, UUID sessionTokenId) {}
}
