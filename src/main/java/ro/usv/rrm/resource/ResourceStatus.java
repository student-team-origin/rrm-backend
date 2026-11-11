package ro.usv.rrm.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Starea logică a unei resurse (enumerarea {@code StareResursa} din docs/openapi.yaml).
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract
 * ({@code Activa}, {@code Mentenanta}, {@code Defecta}).
 */
public enum ResourceStatus {
  ACTIVE("Activa"),
  MAINTENANCE("Mentenanta"),
  BROKEN("Defecta");

  private final String value;

  ResourceStatus(String value) {
    this.value = value;
  }

  /** Valoarea folosită în baza de date și în JSON. */
  @JsonValue
  public String getValue() {
    return value;
  }

  /**
   * Găsește starea după valoarea din baza de date sau din JSON.
   *
   * @throws IllegalArgumentException dacă valoarea nu este o stare cunoscută
   */
  @JsonCreator
  public static ResourceStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(status -> status.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Stare de resursă necunoscută: " + value));
  }
}
