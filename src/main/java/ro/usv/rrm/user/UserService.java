package ro.usv.rrm.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.user.dto.UserResponse;

/** Citirea utilizatorilor și a rolurilor lor. */
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  /**
   * Întoarce utilizatorul cu rolurile sale.
   *
   * @throws ApiException 404 {@code UTILIZATOR_INEXISTENT} dacă utilizatorul nu există
   */
  @Transactional(readOnly = true)
  public UserResponse findById(long id) {
    return userRepository
        .findById(id)
        .map(UserResponse::from)
        .orElseThrow(() ->
            ApiException.notFound(
                "UTILIZATOR_INEXISTENT", "Utilizatorul cu id-ul " + id + " nu există."));
  }
}
