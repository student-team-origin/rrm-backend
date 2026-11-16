package ro.usv.rrm.reservation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Starea unei rezervări (enumerarea {@code StareRezervare} din api-contract/openapi.yaml).
 *
 * <p>Constantele respectă stilul Java; valoarea din baza de date și din JSON este cea din contract.
 */
@Getter(onMethod_ = @JsonValue)
@RequiredArgsConstructor
public enum ReservationStatus {
  PENDING_COORDINATOR("InAsteptareCoordonator"),
  PENDING_LABORATORY("InAsteptareLaborator"),
  APPROVED("Aprobata"),
  ACTIVE("Activa"),
  COMPLETED("Finalizata"),
  REJECTED("Respinsa"),
  CANCELLED("Anulata"),
  AUTO_CANCELLED("AnulataAutomat");

  /** Stările din care solicitantul își poate anula rezervarea (înainte de start). */
  public static final Set<ReservationStatus> CANCELLABLE =
      EnumSet.of(PENDING_COORDINATOR, PENDING_LABORATORY, APPROVED);

  /**
   * Stările care blochează intervalul resursei: aceleași ca în condiția {@code WHERE} a
   * constrângerii {@code rezervare_fara_suprapunere}.
   */
  public static final Set<ReservationStatus> BLOCKING =
      EnumSet.of(PENDING_COORDINATOR, PENDING_LABORATORY, APPROVED, ACTIVE);

  /** Valoarea folosită în baza de date și în JSON. */
  private final String value;

  /**
   * Găsește starea după valoarea din baza de date sau din JSON.
   *
   * @throws IllegalArgumentException dacă valoarea nu este o stare cunoscută
   */
  @JsonCreator
  public static ReservationStatus fromValue(String value) {
    return Arrays.stream(values())
        .filter(status -> status.value.equals(value))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Stare de rezervare necunoscută: " + value));
  }
}
