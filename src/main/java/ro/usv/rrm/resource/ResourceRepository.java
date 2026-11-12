package ro.usv.rrm.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository
    extends JpaRepository<Resource, Long>, JpaSpecificationExecutor<Resource> {

  /** Încarcă și categoria, afișată în fiecare element al listei, fără o interogare per resursă. */
  @Override
  @EntityGraph(attributePaths = "category")
  Page<Resource> findAll(Specification<Resource> spec, Pageable pageable);

  /**
   * Verifică dacă resursa apare în vreo rezervare, indiferent de stare. Interogare nativă, pentru
   * că entitatea rezervării nu este încă mapată.
   */
  @Query(
      value = "SELECT EXISTS (SELECT 1 FROM rezervare WHERE resursa_id = :resourceId)",
      nativeQuery = true)
  boolean hasReservations(@Param("resourceId") Long resourceId);
}
