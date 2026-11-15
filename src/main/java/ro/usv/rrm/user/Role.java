package ro.usv.rrm.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rolul unui utilizator (enumerarea {@code Rol} din api-contract/openapi.yaml).
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract.
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum Role {
  STUDENT("Student"),
  GRANT_COORDINATOR("CoordonatorGrant"),
  LAB_MANAGER("ResponsabilLaborator"),
  SYSTEM_ADMIN("AdministratorSistem");

  /** Valoarea folosită în baza de date și în JSON. */
  private final String value;

  /**
   * Găsește constanta după valoarea din baza de date sau din JSON.
   *
   * @throws IllegalArgumentException dacă valoarea nu este cunoscută
   */
  @JsonCreator
  public static Role fromValue(String value) {
    return Arrays.stream(values())
        .filter(item -> item.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Rol necunoscut: " + value));
  }
}
