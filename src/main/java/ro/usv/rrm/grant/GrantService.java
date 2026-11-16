package ro.usv.rrm.grant;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.grant.dto.GrantResponse;

/** Citirea granturilor și a soldului lor de ore. */
@Service
@RequiredArgsConstructor
public class GrantService {

  private final GrantRepository grantRepository;

  /**
   * Granturile vizibile pentru utilizator: toate pentru Administratorul de sistem, altfel doar cele
   * pe care le coordonează sau din care face parte ca membru.
   */
  @Transactional(readOnly = true)
  public List<GrantResponse> findVisibleTo(AuthenticatedUser user) {
    List<GrantBalance> grants =
        user.isSystemAdmin()
            ? grantRepository.findAllWithBalance()
            : grantRepository.findAllWithBalanceForUser(user.userId());
    return grants.stream().map(GrantResponse::from).toList();
  }

  /**
   * Detaliile unui grant, cu soldul de ore. Drepturile se verifică în controller ({@code
   * @grantAccess}).
   *
   * @throws ApiException 404 {@code GRANT_INEXISTENT}
   */
  @Transactional(readOnly = true)
  public GrantResponse findById(long grantId) {
    return grantRepository
        .findWithBalanceById(grantId)
        .map(GrantResponse::from)
        .orElseThrow(() -> notFound(grantId));
  }

  /**
   * Blochează grantul până la finalul tranzacției apelantului și îi întoarce soldul de ore.
   * Cererile de rezervare pe același grant se serializează astfel, ca două cereri simultane să nu
   * treacă amândouă de verificarea soldului. Cere o tranzacție deja pornită ({@code MANDATORY}):
   * fără ea, blocarea s-ar elibera imediat.
   *
   * @throws ApiException 404 {@code GRANT_INEXISTENT}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public GrantResponse getBalanceForUpdate(long grantId) {
    grantRepository.lockById(grantId).orElseThrow(() -> notFound(grantId));
    // Citit după blocare: soldul include rezervările confirmate de cererile serializate înainte.
    return findById(grantId);
  }

  private static ApiException notFound(long grantId) {
    return ApiException.notFound("GRANT_INEXISTENT", "Grantul cu id-ul " + grantId + " nu există.");
  }
}
