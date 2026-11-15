package ro.usv.rrm.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationStatus;
import ro.usv.rrm.resource.dto.ResourceResponse;
import ro.usv.rrm.user.dto.UserResponse;

/**
 * Detaliul unei rezervări (schema {@code RezervareDetaliu} din api-contract/openapi.yaml):
 * câmpurile din {@code Rezervare}, plus resursa, solicitantul și istoricul aprobărilor.
 *
 * <p>Se construiește în Service, în tranzacție, pentru că resursa și solicitantul sunt LAZY.
 *
 * @param id id-ul rezervării
 * @param resursaId id-ul resursei rezervate
 * @param grantId id-ul grantului
 * @param solicitantId id-ul solicitantului
 * @param stare starea rezervării
 * @param start începutul intervalului
 * @param sfarsit sfârșitul intervalului (fără timpul de pregătire)
 * @param oreSolicitate orele scăzute din soldul grantului
 * @param scop scopul declarat al rezervării
 * @param dataCreare momentul cererii
 * @param dataCheckIn momentul check-in-ului, dacă a avut loc
 * @param resursa resursa rezervată
 * @param solicitant utilizatorul care a cerut rezervarea, cu rolurile sale
 * @param aprobari deciziile aprobatorilor, în ordinea nivelurilor
 */
public record ReservationDetailResponse(
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
    Instant dataCheckIn,
    ResourceResponse resursa,
    UserResponse solicitant,
    List<ApprovalResponse> aprobari) {

  public static ReservationDetailResponse from(
      Reservation reservation, List<ApprovalResponse> approvals) {
    ReservationResponse summary = ReservationResponse.from(reservation);
    return new ReservationDetailResponse(
        summary.id(),
        summary.resursaId(),
        summary.grantId(),
        summary.solicitantId(),
        summary.stare(),
        summary.start(),
        summary.sfarsit(),
        summary.oreSolicitate(),
        summary.scop(),
        summary.dataCreare(),
        summary.dataCheckIn(),
        ResourceResponse.from(reservation.getResource()),
        UserResponse.from(reservation.getRequester()),
        approvals);
  }
}
