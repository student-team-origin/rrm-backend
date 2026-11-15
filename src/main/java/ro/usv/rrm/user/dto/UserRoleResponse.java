package ro.usv.rrm.user.dto;

import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.UserRole;

/**
 * Un rol al utilizatorului (schema {@code UtilizatorRol} din api-contract/openapi.yaml).
 *
 * @param rol rolul
 * @param tipScop domeniul în care se aplică rolul
 * @param scopId id-ul grantului sau al laboratorului; {@code null} pentru scopul global
 */
public record UserRoleResponse(Role rol, ScopeType tipScop, Long scopId) {

  public static UserRoleResponse from(UserRole role) {
    return new UserRoleResponse(role.getRole(), role.getScopeType(), role.getScopeId());
  }
}
