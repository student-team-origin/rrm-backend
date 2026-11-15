package ro.usv.rrm.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Răspunsul la autentificare (schema {@code LoginResponse} din api-contract/openapi.yaml): fie
 * tokenurile, fie o provocare 2FA.
 *
 * @param twoFactorRequired {@code true} dacă mai trebuie introdus codul 2FA
 * @param challengeId provocarea 2FA, prezentă doar când {@code twoFactorRequired} este {@code true}
 * @param tokens tokenurile, prezente doar când {@code twoFactorRequired} este {@code false}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(boolean twoFactorRequired, String challengeId, AuthTokens tokens) {

  public static LoginResponse authenticated(AuthTokens tokens) {
    return new LoginResponse(false, null, tokens);
  }

  public static LoginResponse twoFactorChallenge(String challengeId) {
    return new LoginResponse(true, challengeId, null);
  }
}
