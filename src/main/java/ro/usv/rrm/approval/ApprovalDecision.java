package ro.usv.rrm.approval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Decizia unui aprobator (enumerarea {@code Decizie} din api-contract/openapi.yaml).
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract.
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum ApprovalDecision {
  APPROVED("Aprobat"),
  REJECTED("Respins");

  /** Valoarea folosită în baza de date și în JSON. */
  private final String value;

  /**
   * Găsește decizia după valoarea din baza de date sau din JSON.
   *
   * @throws IllegalArgumentException dacă valoarea nu este o decizie cunoscută
   */
  @JsonCreator
  public static ApprovalDecision fromValue(String value) {
    return Arrays.stream(values())
        .filter(decision -> decision.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Decizie necunoscută: " + value));
  }
}
