package ro.usv.rrm.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cererea de autentificare (schema {@code LoginRequest} din api-contract/openapi.yaml).
 *
 * @param email e-mailul instituțional
 * @param parola parola în clar, maximum 72 de caractere (doar în cerere, prin HTTPS; nu se loghează
 *     și nu se salvează)
 * @param deviceToken tokenul dispozitivului de încredere, dacă există (folosit în TO-32)
 */
public record LoginRequest(
    @NotBlank(message = "E-mailul este obligatoriu.")
        @Email(message = "E-mailul nu are un format valid.")
        String email,
    // BCrypt folosește doar primii 72 de octeți ai parolei; restul ar fi ignorat în tăcere.
    @NotBlank(message = "Parola este obligatorie.")
        @Size(max = 72, message = "Parola poate avea cel mult 72 de caractere.")
        String parola,
    String deviceToken) {

  /** Fără parolă, ca să nu ajungă în loguri dacă cererea este afișată. */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}
