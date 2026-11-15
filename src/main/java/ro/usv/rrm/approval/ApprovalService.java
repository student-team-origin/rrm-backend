package ro.usv.rrm.approval;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.dto.ApprovalResponse;

/**
 * Aprobările rezervărilor. Deocamdată doar citirea istoricului; deciziile vin în fluxul de aprobare
 * (TO-6).
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

  private final ApprovalRepository approvalRepository;

  /** Istoricul aprobărilor unei rezervări, în ordinea nivelurilor (1, apoi 2). */
  @Transactional(readOnly = true)
  public List<ApprovalResponse> findByReservation(long reservationId) {
    return approvalRepository.findByReservationIdOrderByLevelAsc(reservationId).stream()
        .map(ApprovalResponse::from)
        .toList();
  }

  /**
   * Istoricul aprobărilor pentru mai multe rezervări, într-o singură interogare, grupat pe
   * rezervare. O rezervare fără aprobări lipsește din rezultat.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<ApprovalResponse>> findByReservations(Collection<Long> reservationIds) {
    if (reservationIds.isEmpty()) {
      return Map.of();
    }
    return approvalRepository.findByReservationIdInOrderByLevelAsc(reservationIds).stream()
        .map(ApprovalResponse::from)
        .collect(Collectors.groupingBy(ApprovalResponse::rezervareId));
  }

  /** {@code true} dacă utilizatorul a aprobat sau a respins deja rezervarea. */
  @Transactional(readOnly = true)
  public boolean hasDecided(long reservationId, long userId) {
    return approvalRepository.existsByReservationIdAndApproverId(reservationId, userId);
  }
}
