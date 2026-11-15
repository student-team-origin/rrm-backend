package ro.usv.rrm.reservation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
    extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

  /**
   * Citește rezervarea și îi blochează rândul ({@code SELECT ... FOR UPDATE}) până la finalul
   * tranzacției, ca anularea să nu se suprapună cu o aprobare sau cu check-in-ul aceleiași
   * rezervări.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Reservation r WHERE r.id = :id")
  Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
