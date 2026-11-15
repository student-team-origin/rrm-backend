package ro.usv.rrm.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.user.dto.UserResponse;

/** Citirea utilizatorilor și a rolurilor lor. Drepturile se verifică în {@link UserController}. */
@Service
@RequiredArgsConstructor
public class UserService {

  static final int MAX_PAGE_SIZE = 100;

  private final UserRepository userRepository;

  /**
   * Caută utilizatorii după rol și text (nume sau e-mail), ordonați după nume.
   *
   * @param page numărul paginii, de la 0
   * @param size dimensiunea paginii, între 1 și {@value #MAX_PAGE_SIZE}
   */
  @Transactional(readOnly = true)
  public PageResponse<UserResponse> search(Role role, String text, int page, int size) {
    PageRequest pageRequest =
        PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by("name").and(Sort.by("id")));
    return PageResponse.from(
        userRepository.findAll(UserSpecifications.matching(role, text), pageRequest),
        UserResponse::from);
  }

  /**
   * Întoarce utilizatorul cu rolurile sale.
   *
   * @throws ApiException 404 {@code UTILIZATOR_INEXISTENT} dacă utilizatorul nu există
   */
  @Transactional(readOnly = true)
  public UserResponse findById(long id) {
    return UserResponse.from(getUser(id));
  }

  /**
   * Entitatea utilizatorului, pentru asocierile din alte module (ex. aprobatorul unei decizii).
   *
   * @throws ApiException 404 {@code UTILIZATOR_INEXISTENT} dacă utilizatorul nu există
   */
  @Transactional(readOnly = true)
  public User getUser(long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() ->
            ApiException.notFound(
                "UTILIZATOR_INEXISTENT", "Utilizatorul cu id-ul " + id + " nu există."));
  }
}
