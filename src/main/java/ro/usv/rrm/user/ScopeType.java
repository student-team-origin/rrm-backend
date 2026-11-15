package ro.usv.rrm.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domeniul în care se aplică un rol (enumerarea {@code TipScop} din api-contract/openapi.yaml):
 * global, pe un grant sau pe un laborator.
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract.
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum ScopeType {
  GLOBAL("Global"),
  GRANT("Grant"),
  LABORATORY("Laborator");

  /** Valoarea folosită în baza de date și în JSON. */
  private final String value;

  /**
   * Găsește constanta după valoarea din baza de date sau din JSON.
   *
   * @throws IllegalArgumentException dacă valoarea nu este cunoscută
   */
  @JsonCreator
  public static ScopeType fromValue(String value) {
    return Arrays.stream(values())
        .filter(item -> item.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Tip de scop necunoscut: " + value));
  }
}
