package ro.usv.rrm.reservation;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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

  /**
   * Pentru listele de detalii (aprobări): încarcă în aceeași interogare resursa, categoria,
   * laboratorul și solicitantul, nu câte o interogare per rezervare.
   */
  @Override
  @EntityGraph(attributePaths = {"resource.category", "resource.laboratory", "requester"})
  List<Reservation> findAll(Specification<Reservation> spec, Sort sort);

  /** Ca {@link #findAll(Specification, Sort)}, pentru rezervările cu id-urile date. */
  @EntityGraph(attributePaths = {"resource.category", "resource.laboratory", "requester"})
  List<Reservation> findWithDetailsByIdIn(Collection<Long> ids);
}
