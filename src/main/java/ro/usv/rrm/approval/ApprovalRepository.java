package ro.usv.rrm.approval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

  /** Istoricul aprobărilor unei rezervări, în ordinea nivelurilor. */
  List<Approval> findByReservationIdOrderByLevelAsc(Long reservationId);

  /** {@code true} dacă utilizatorul a luat deja o decizie asupra rezervării. */
  boolean existsByReservationIdAndApproverId(Long reservationId, Long approverId);

  /**
   * Rezervările asupra cărora utilizatorul a luat decizia dată, cele mai recente decizii primele.
   */
  @Query(
      "SELECT a.reservation.id FROM Approval a"
          + " WHERE a.approver.id = :approverId AND a.decision = :decision"
          + " ORDER BY a.decidedAt DESC, a.id DESC")
  List<Long> findReservationIdsDecidedBy(
      @Param("approverId") Long approverId, @Param("decision") ApprovalDecision decision);
}
