package ro.usv.rrm.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un rol al unui utilizator, cu scopul în care se aplică (tabela {@code utilizator_rol}). De
 * exemplu, Coordonator Grant pentru grantul 1 sau Responsabil Laborator pentru laboratorul 2.
 */
@Entity
@Table(name = "utilizator_rol")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "utilizator_id", nullable = false)
  private User user;

  @Column(name = "rol", nullable = false, length = 30)
  private Role role;

  @Column(name = "tip_scop", nullable = false, length = 20)
  private ScopeType scopeType;

  /** Id-ul grantului sau al laboratorului; {@code null} pentru scopul global. */
  @Column(name = "scop_id")
  private Long scopeId;

  public UserRole(User user, Role role, ScopeType scopeType, Long scopeId) {
    this.user = user;
    this.role = role;
    this.scopeType = scopeType;
    this.scopeId = scopeId;
  }
}
