package ro.usv.rrm.approval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

  /** Istoricul aprobărilor unei rezervări, în ordinea nivelurilor. */
  List<Approval> findByReservationIdOrderByLevelAsc(Long reservationId);

  /** {@code true} dacă utilizatorul a luat deja o decizie asupra rezervării. */
  boolean existsByReservationIdAndApproverId(Long reservationId, Long approverId);
}
