package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;

/** Regulile RBAC cu scop, pe utilizatorul din {@code SecurityContext}. */
class AccessControlTest {

  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");

  private final AccessControl access = new AccessControl();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void systemAdmin_hasGlobalAccess() {
    signIn(1, new ScopedRole(Role.SYSTEM_ADMIN, ScopeType.GLOBAL, null));

    assertThat(access.isSystemAdmin()).isTrue();
    assertThat(access.canManageGrant(99L)).isTrue();
    assertThat(access.canManageLaboratory(99L)).isTrue();
    assertThat(access.isSelfOrAdmin(6L)).isTrue();
  }

  @Test
  void grantCoordinator_managesOnlyOwnGrant() {
    signIn(5, new ScopedRole(Role.GRANT_COORDINATOR, ScopeType.GRANT, 2L));

    assertThat(access.canManageGrant(2L)).isTrue();
    assertThat(access.canManageGrant(1L)).isFalse();
    assertThat(access.canManageLaboratory(2L)).isFalse();
    assertThat(access.isSystemAdmin()).isFalse();
  }

  @Test
  void labManager_managesOnlyOwnLaboratory() {
    signIn(3, new ScopedRole(Role.LAB_MANAGER, ScopeType.LABORATORY, 2L));

    assertThat(access.canManageLaboratory(2L)).isTrue();
    assertThat(access.canManageLaboratory(1L)).isFalse();
    // Același id, dar alt tip de scop: laboratorul 2 nu înseamnă grantul 2.
    assertThat(access.canManageGrant(2L)).isFalse();
  }

  @Test
  void student_hasNoManagementRights() {
    signIn(6, new ScopedRole(Role.STUDENT, ScopeType.GLOBAL, null));

    assertThat(access.isSystemAdmin()).isFalse();
    assertThat(access.canManageGrant(1L)).isFalse();
    assertThat(access.canManageLaboratory(1L)).isFalse();
    assertThat(access.isSelfOrAdmin(6L)).isTrue();
    assertThat(access.isSelfOrAdmin(7L)).isFalse();
  }

  @Test
  void nullScopeId_isNeverMatched() {
    signIn(3, new ScopedRole(Role.LAB_MANAGER, ScopeType.LABORATORY, 2L));

    assertThat(access.canManageLaboratory(null)).isFalse();
  }

  @Test
  void anonymous_hasNoAccess() {
    assertThat(access.currentUser()).isEmpty();
    assertThat(access.isSelfOrAdmin(6L)).isFalse();
    assertThat(access.canManageLaboratory(1L)).isFalse();
  }

  private static void signIn(long userId, ScopedRole... roles) {
    AuthenticatedUser user = new AuthenticatedUser(userId, SESSION_ID, List.of(roles));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
  }
}
