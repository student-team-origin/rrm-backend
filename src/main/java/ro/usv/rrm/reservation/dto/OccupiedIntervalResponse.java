package ro.usv.rrm.reservation.dto;

import java.time.Instant;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationStatus;

/**
 * Un interval în care resursa este ocupată (schema {@code IntervalOcupat} din
 * api-contract/openapi.yaml). Nu spune cine a rezervat: calendarul îl vede orice utilizator.
 *
 * @param start începutul rezervării
 * @param sfarsit sfârșitul rezervării, inclusiv timpul de pregătire al resursei
 * @param stare starea rezervării
 */
public record OccupiedIntervalResponse(Instant start, Instant sfarsit, ReservationStatus stare) {

  public static OccupiedIntervalResponse from(Reservation reservation) {
    return new OccupiedIntervalResponse(
        reservation.getStart(), reservation.getBlockedEnd(), reservation.getStatus());
  }
}
