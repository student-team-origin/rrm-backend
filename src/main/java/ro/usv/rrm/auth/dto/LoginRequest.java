package ro.usv.rrm.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cererea de autentificare (schema {@code LoginRequest} din api-contract/openapi.yaml).
 *
 * @param email e-mailul instituțional
 * @param parola parola în clar (doar în cerere, prin HTTPS; nu se loghează și nu se salvează)
 * @param deviceToken tokenul dispozitivului de încredere, dacă există (folosit în TO-32)
 */
public record LoginRequest(
    @NotBlank(message = "E-mailul este obligatoriu.")
        @Email(message = "E-mailul nu are un format valid.")
        String email,
    @NotBlank(message = "Parola este obligatorie.") String parola,
    String deviceToken) {

  /** Fără parolă, ca să nu ajungă în loguri dacă cererea este afișată. */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}
