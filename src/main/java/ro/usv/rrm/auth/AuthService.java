package ro.usv.rrm.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.JwtService.IssuedToken;
import ro.usv.rrm.auth.dto.AuthTokens;
import ro.usv.rrm.auth.dto.LoginRequest;
import ro.usv.rrm.auth.dto.LoginResponse;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.user.User;
import ro.usv.rrm.user.UserRepository;

/** Autentificarea cu e-mail și parolă: verificarea credențialelor și deschiderea sesiunii. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  /**
   * Hash BCrypt al unei valori aleatoare, comparat când e-mailul nu există, ca răspunsul să dureze
   * la fel ca pentru o parolă greșită și să nu dezvăluie ce adrese au cont.
   */
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$0cuZW3a1Dkm7bKAvaTE.TOi0slMBxyyy6f9YlUrb1GlM9CtOa8NrS";

  private final UserRepository userRepository;
  private final UserSessionRepository sessionRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RrmProperties properties;
  private final Clock clock;

  /**
   * Verifică e-mailul și parola. Dacă utilizatorul are 2FA activ, întoarce o provocare 2FA; altfel
   * deschide o sesiune și întoarce tokenul de acces.
   *
   * @throws ApiException 401 {@code CREDENTIALE_INVALIDE} pentru e-mail inexistent sau parolă
   *     greșită (același mesaj în ambele cazuri); 401 {@code CONT_DEZACTIVAT} pentru un cont
   *     dezactivat
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
    String hash = user != null ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
    if (!passwordEncoder.matches(request.parola(), hash) || user == null) {
      log.info("Autentificare eșuată pentru {}", email);
      throw ApiException.unauthorized("CREDENTIALE_INVALIDE", "E-mail sau parolă incorecte.");
    }
    if (!user.isActive()) {
      throw ApiException.unauthorized(
          "CONT_DEZACTIVAT", "Contul este dezactivat. Contactează administratorul.");
    }

    // TODO: TO-32 - fără provocare 2FA dacă request.deviceToken() este un dispozitiv de încredere.
    if (user.isTwoFactorEnabled()) {
      return LoginResponse.twoFactorChallenge(jwtService.issueTwoFactorChallenge(user.getId()));
    }
    return LoginResponse.authenticated(openSession(user));
  }

  /** Salvează o sesiune nouă în tabela {@code sesiune} și emite tokenul de acces pentru ea. */
  AuthTokens openSession(User user) {
    Instant now = clock.instant();
    UUID tokenId = UUID.randomUUID();
    sessionRepository.save(
        new UserSession(user, tokenId, now, now.plus(properties.auth().sessionIdleTimeout())));
    IssuedToken token = jwtService.issueAccessToken(user.getId(), tokenId);
    log.info("Utilizatorul {} s-a autentificat", user.getId());
    return new AuthTokens(token.value(), token.ttl().toSeconds(), null);
  }
}
