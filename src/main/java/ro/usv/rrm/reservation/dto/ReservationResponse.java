package ro.usv.rrm.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationStatus;

/**
 * O rezervare (schema {@code Rezervare} din api-contract/openapi.yaml).
 *
 * @param id id-ul rezervării
 * @param resursaId id-ul resursei rezervate
 * @param grantId id-ul grantului pe ale cărui ore se face rezervarea
 * @param solicitantId id-ul utilizatorului care a cerut rezervarea
 * @param stare starea rezervării
 * @param start începutul intervalului
 * @param sfarsit sfârșitul intervalului (fără timpul de pregătire)
 * @param oreSolicitate orele scăzute din soldul grantului
 * @param scop scopul declarat al rezervării
 * @param dataCreare momentul cererii
 * @param dataCheckIn momentul check-in-ului, dacă a avut loc
 */
public record ReservationResponse(
    Long id,
    Long resursaId,
    Long grantId,
    Long solicitantId,
    ReservationStatus stare,
    Instant start,
    Instant sfarsit,
    BigDecimal oreSolicitate,
    String scop,
    Instant dataCreare,
    Instant dataCheckIn) {

  /** Asocierile LAZY se citesc doar prin id, deci nu declanșează interogări suplimentare. */
  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getResource().getId(),
        reservation.getGrantId(),
        reservation.getRequester().getId(),
        reservation.getStatus(),
        reservation.getStart(),
        reservation.getEnd(),
        reservation.getRequestedHours(),
        reservation.getPurpose(),
        reservation.getCreatedAt(),
        reservation.getCheckInAt());
  }
}
