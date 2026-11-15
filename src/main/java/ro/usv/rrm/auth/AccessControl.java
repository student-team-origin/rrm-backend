package ro.usv.rrm.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Verificările RBAC cu scop pentru {@code @PreAuthorize}, pe utilizatorul cererii curente.
 *
 * <pre>{@code
 * @PreAuthorize("@access.canManageLaboratory(#request.laboratorId())")
 * }</pre>
 *
 * <p>Administratorul de sistem are acces global în toate metodele. Verificările care au nevoie de
 * date din baza de date (laboratorul unei resurse, membrii unui grant) stau în modulul respectiv
 * (ex. {@code @resourceAccess}, {@code @grantAccess}) și folosesc metodele de aici.
 */
@Component("access")
public class AccessControl {

  /** Utilizatorul autentificat al cererii curente, dacă există. */
  public Optional<AuthenticatedUser> currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
        ? Optional.of(user)
        : Optional.empty();
  }

  /** Administratorul de sistem. */
  public boolean isSystemAdmin() {
    return currentUser().map(AuthenticatedUser::isSystemAdmin).orElse(false);
  }

  /** Administratorul sau utilizatorul însuși (ex. propriul profil). */
  public boolean isSelfOrAdmin(Long userId) {
    return currentUser().map(u -> u.isSystemAdmin() || u.userId() == userId).orElse(false);
  }

  /** Administratorul sau Coordonatorul grantului dat. */
  public boolean canManageGrant(Long grantId) {
    return currentUser().map(u -> u.isSystemAdmin() || u.coordinatesGrant(grantId)).orElse(false);
  }

  /** Administratorul sau Responsabilul laboratorului dat (ex. resursele laboratorului). */
  public boolean canManageLaboratory(Long laboratoryId) {
    return currentUser()
        .map(u -> u.isSystemAdmin() || u.managesLaboratory(laboratoryId))
        .orElse(false);
  }
}
