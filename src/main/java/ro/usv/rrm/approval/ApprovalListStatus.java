package ro.usv.rrm.approval;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Filtrul listei de aprobări ({@code GET /approvals?stare=...} din api-contract/openapi.yaml):
 * cererile care așteaptă decizia utilizatorului sau cele pe care le-a aprobat ori respins deja.
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum ApprovalListStatus {
  PENDING("inAsteptare"),
  APPROVED("aprobate"),
  REJECTED("respinse");

  /** Valoarea parametrului din URL. */
  private final String value;

  /**
   * Găsește filtrul după valoarea din URL.
   *
   * @throws IllegalArgumentException dacă valoarea nu este cunoscută
   */
  public static ApprovalListStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(status -> status.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Filtru de aprobări necunoscut: " + value));
  }
}
