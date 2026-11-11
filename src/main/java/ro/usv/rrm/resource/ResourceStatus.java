package ro.usv.rrm.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Starea logică a unei resurse (enumerarea {@code StareResursa} din docs/openapi.yaml).
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract
 * ({@code Activa}, {@code Mentenanta}, {@code Defecta}).
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum ResourceStatus {
  ACTIVE("Activa"),
  MAINTENANCE("Mentenanta"),
  BROKEN("Defecta");

  /** Valoarea folosită în baza de date și în JSON. */
  private final String value;

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
