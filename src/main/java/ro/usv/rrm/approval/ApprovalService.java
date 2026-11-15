package ro.usv.rrm.approval;

import java.util.List;
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

  /** {@code true} dacă utilizatorul a aprobat sau a respins deja rezervarea. */
  @Transactional(readOnly = true)
  public boolean hasDecided(long reservationId, long userId) {
    return approvalRepository.existsByReservationIdAndApproverId(reservationId, userId);
  }
}
