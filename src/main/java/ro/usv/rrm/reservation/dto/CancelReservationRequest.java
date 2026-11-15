package ro.usv.rrm.reservation.dto;

import jakarta.validation.constraints.Size;

/**
 * Cererea opțională de anulare ({@code POST /reservations/{id}/cancel}).
 *
 * @param motiv motivul anulării, opțional
 */
public record CancelReservationRequest(
    @Size(max = 500, message = "Motivul poate avea cel mult 500 de caractere.") String motiv) {}
