package ro.usv.rrm.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tokenurile emise după autentificare (schema {@code AuthTokens} din api-contract/openapi.yaml).
 *
 * @param accessToken tokenul JWT, trimis apoi în header-ul {@code Authorization: Bearer}
 * @param expiresIn secunde până la expirarea tokenului
 * @param deviceToken tokenul dispozitivului de încredere, doar după 2FA cu „ține minte” (TO-32)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokens(String accessToken, long expiresIn, String deviceToken) {}
