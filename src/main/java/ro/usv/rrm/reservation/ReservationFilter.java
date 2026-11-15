package ro.usv.rrm.reservation;

import java.time.Instant;

/**
 * Filtrele opționale pentru „Rezervările mele” ({@code GET /reservations}). Un filtru {@code null}
 * nu restrânge rezultatul; filtrele date se combină cu „și”.
 *
 * @param status starea rezervării
 * @param from rezervările care se termină după acest moment
 * @param to rezervările care încep înainte de acest moment
 */
public record ReservationFilter(ReservationStatus status, Instant from, Instant to) {}
