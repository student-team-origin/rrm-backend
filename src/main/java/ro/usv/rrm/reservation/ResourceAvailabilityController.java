package ro.usv.rrm.reservation;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.usv.rrm.reservation.dto.OccupiedIntervalResponse;

/**
 * Disponibilitatea unei resurse pentru calendar ({@code GET /resources/{id}/availability}, tag-ul
 * {@code Resurse} din contract). Stă în modulul de rezervări, pentru că datele vin din rezervări:
 * modulul de resurse nu poate depinde de cel de rezervări (care depinde deja de el). Citirea este
 * permisă oricărui utilizator autentificat.
 */
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
public class ResourceAvailabilityController {

  private final ReservationService reservationService;

  @GetMapping("/{resourceId}/availability")
  public List<OccupiedIntervalResponse> getAvailability(
      @PathVariable Long resourceId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    return reservationService.findOccupiedIntervals(resourceId, from.toInstant(), to.toInstant());
  }
}
