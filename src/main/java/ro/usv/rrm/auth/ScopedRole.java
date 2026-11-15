package ro.usv.rrm.auth;

import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.UserRole;

/**
 * Un rol al utilizatorului autentificat, cu scopul lui (din tabela {@code utilizator_rol}).
 *
 * @param role rolul
 * @param scopeType domeniul: global, un grant sau un laborator
 * @param scopeId id-ul grantului sau al laboratorului; {@code null} pentru scopul global
 */
public record ScopedRole(Role role, ScopeType scopeType, Long scopeId) {

  static ScopedRole from(UserRole role) {
    return new ScopedRole(role.getRole(), role.getScopeType(), role.getScopeId());
  }

  /** {@code true} dacă este rolul dat, pe scopul dat. */
  boolean matches(Role expectedRole, ScopeType expectedScope, Long expectedScopeId) {
    return role == expectedRole
        && scopeType == expectedScope
        && expectedScopeId != null
        && expectedScopeId.equals(scopeId);
  }
}
