package ro.usv.rrm.grant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.AccessControl;

/**
 * Cine poate vedea un grant, pentru {@code @PreAuthorize("@grantAccess.canView(#grantId)")}:
 * Administratorul de sistem, Coordonatorul grantului și membrii lui.
 */
@Component("grantAccess")
@RequiredArgsConstructor
public class GrantAccess {

  private final GrantRepository grantRepository;
  private final AccessControl access;

  /**
   * {@code true} dacă utilizatorul curent poate vedea grantul. Pentru un grant inexistent întoarce
   * {@code true}, ca Service-ul să răspundă cu 404, nu 403.
   */
  @Transactional(readOnly = true)
  public boolean canView(Long grantId) {
    return access
        .currentUser()
        .map(user ->
            user.isSystemAdmin()
                || user.coordinatesGrant(grantId)
                || grantRepository.isMemberOrMissing(grantId, user.userId()))
        .orElse(false);
  }

  /**
   * {@code true} dacă utilizatorul curent poate rezerva pe orele grantului: aceiași utilizatori
   * care îl pot vedea (Administratorul, Coordonatorul și membrii). Pentru un grant inexistent
   * întoarce {@code true}, ca Service-ul să răspundă cu 404, nu 403.
   */
  @Transactional(readOnly = true)
  public boolean canReserve(Long grantId) {
    return canView(grantId);
  }
}
