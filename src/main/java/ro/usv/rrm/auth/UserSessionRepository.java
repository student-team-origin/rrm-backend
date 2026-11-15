package ro.usv.rrm.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

  Optional<UserSession> findByTokenId(UUID tokenId);
}
