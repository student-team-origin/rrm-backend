package ro.usv.rrm.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

  /** Încarcă și utilizatorul, verificat la fiecare cerere (cont activ). */
  @EntityGraph(attributePaths = "user")
  Optional<UserSession> findByTokenId(UUID tokenId);
}
