package ro.usv.rrm.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

  /**
   * Încarcă, în aceeași interogare, și utilizatorul (cont activ) și rolurile lui, necesare la
   * fiecare cerere pentru RBAC.
   */
  @EntityGraph(attributePaths = {"user", "user.roles"})
  Optional<UserSession> findByTokenId(UUID tokenId);
}
