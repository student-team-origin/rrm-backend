package ro.usv.rrm.user.dto;

import java.util.List;
import ro.usv.rrm.user.User;

/**
 * Un utilizator, cu rolurile sale (schema {@code Utilizator} din api-contract/openapi.yaml).
 *
 * <p>Se construiește în Service, în tranzacție, pentru că lista de roluri este LAZY.
 *
 * @param id id-ul utilizatorului
 * @param nume numele complet
 * @param email e-mailul instituțional
 * @param activ dacă contul este activ
 * @param doiFactoriActiv dacă autentificarea în doi pași este activă
 * @param roluri rolurile, cu scopul fiecăruia
 */
public record UserResponse(
    Long id,
    String nume,
    String email,
    boolean activ,
    boolean doiFactoriActiv,
    List<UserRoleResponse> roluri) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.isActive(),
        user.isTwoFactorEnabled(),
        user.getRoles().stream().map(UserRoleResponse::from).toList());
  }
}
