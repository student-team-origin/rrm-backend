package ro.usv.rrm.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /** Caută după e-mailul deja normalizat cu {@link User#normalizeEmail}. */
  Optional<User> findByEmail(String email);
}
