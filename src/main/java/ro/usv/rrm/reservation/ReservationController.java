package ro.usv.rrm.reservation;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.reservation.dto.CancelReservationRequest;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;

/** Rezervările utilizatorului curent (tag-ul {@code Rezervări} din contract). */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final ReservationService reservationService;

  @GetMapping
  public PageResponse<ReservationResponse> getReservations(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) ReservationStatus stare,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    ReservationFilter filter =
        new ReservationFilter(
            stare, from == null ? null : from.toInstant(), to == null ? null : to.toInstant());
    return reservationService.findMine(user.userId(), filter, page, size);
  }

  @GetMapping("/{reservationId}")
  public ReservationDetailResponse getReservation(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long reservationId) {
    return reservationService.findById(reservationId, user);
  }

  @PostMapping("/{reservationId}/cancel")
  public ReservationResponse cancelReservation(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable Long reservationId,
      @Valid @RequestBody(required = false) CancelReservationRequest request) {
    String reason = request == null ? null : request.motiv();
    return reservationService.cancel(reservationId, user.userId(), reason);
  }
}
