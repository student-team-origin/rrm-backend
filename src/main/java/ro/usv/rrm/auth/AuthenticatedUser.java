package ro.usv.rrm.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;

/**
 * Utilizatorul autentificat al cererii curente, pus în {@code SecurityContext} de {@link
 * JwtAuthenticationFilter}. În controllere se primește cu {@code @AuthenticationPrincipal}.
 *
 * <p>Rolurile se încarcă o singură dată per cerere, odată cu sesiunea. Verificările pe scop
 * (grantul coordonat, laboratorul gestionat) se fac cu metodele de mai jos sau, pe endpoint-uri, cu
 * {@code @PreAuthorize("@access...")} (vezi {@link AccessControl}).
 *
 * @param userId id-ul utilizatorului
 * @param sessionTokenId {@code jti}-ul sesiunii din tabela {@code sesiune}
 * @param roles rolurile utilizatorului, cu scopul fiecăruia
 */
public record AuthenticatedUser(long userId, UUID sessionTokenId, List<ScopedRole> roles) {

  public AuthenticatedUser {
    roles = List.copyOf(roles);
  }

  /**
   * Rolurile ca authorities Spring Security ({@code ROLE_STUDENT}, {@code ROLE_SYSTEM_ADMIN}...),
   * pentru {@code @PreAuthorize("hasRole('SYSTEM_ADMIN')")}. Scopul nu intră aici.
   */
  public List<GrantedAuthority> authorities() {
    return roles.stream()
        .map(ScopedRole::role)
        .distinct()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
        .toList();
  }

  /** Administratorul de sistem: acces global. */
  public boolean isSystemAdmin() {
    return roles.stream().anyMatch(r -> r.role() == Role.SYSTEM_ADMIN);
  }

  /** {@code true} dacă utilizatorul este Coordonatorul grantului dat. */
  public boolean coordinatesGrant(Long grantId) {
    return roles.stream()
        .anyMatch(r -> r.matches(Role.GRANT_COORDINATOR, ScopeType.GRANT, grantId));
  }

  /** {@code true} dacă utilizatorul este Responsabilul laboratorului dat. */
  public boolean managesLaboratory(Long laboratoryId) {
    return roles.stream()
        .anyMatch(r -> r.matches(Role.LAB_MANAGER, ScopeType.LABORATORY, laboratoryId));
  }
}
