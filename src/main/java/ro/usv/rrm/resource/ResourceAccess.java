package ro.usv.rrm.resource;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.AccessControl;

/**
 * Cine poate modifica o resursă, pentru {@code @PreAuthorize("@resourceAccess.canManage(#id)")}:
 * Responsabilul laboratorului căruia îi aparține resursa și Administratorul de sistem.
 */
@Component("resourceAccess")
@RequiredArgsConstructor
public class ResourceAccess {

  private final ResourceRepository resourceRepository;
  private final AccessControl access;

  /**
   * {@code true} dacă utilizatorul curent poate modifica resursa. Pentru o resursă inexistentă
   * întoarce {@code true}, ca Service-ul să răspundă cu 404 {@code RESURSA_INEXISTENTA}, nu 403.
   */
  @Transactional(readOnly = true)
  public boolean canManage(Long resourceId) {
    return access.isSystemAdmin()
        || resourceRepository
            .findLaboratoryIdById(resourceId)
            .map(access::canManageLaboratory)
            .orElse(true);
  }
}
