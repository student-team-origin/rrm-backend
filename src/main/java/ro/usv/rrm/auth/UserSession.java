package ro.usv.rrm.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ro.usv.rrm.user.User;

/**
 * Sesiunea creată la un login reușit (tabela {@code sesiune}).
 *
 * <p>Tokenul JWT conține {@code jti}-ul sesiunii, deci sesiunea poate fi închisă pe server (logout,
 * inactivitate) chiar dacă tokenul nu a expirat încă. Verificarea la fiecare cerere se face în
 * filtrul JWT (TO-31).
 */
@Entity
@Table(name = "sesiune")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "utilizator_id", nullable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private User user;

  @Column(name = "jti", nullable = false, unique = true, updatable = false)
  @Setter(AccessLevel.NONE)
  private UUID tokenId;

  @Column(name = "ultima_activitate", nullable = false)
  private Instant lastActivityAt;

  /** Momentul în care sesiunea expiră din cauza inactivității. */
  @Column(name = "expira_la", nullable = false)
  private Instant expiresAt;

  @Column(name = "revocata", nullable = false)
  private boolean revoked;

  public UserSession(User user, UUID tokenId, Instant now, Instant expiresAt) {
    this.user = user;
    this.tokenId = tokenId;
    this.lastActivityAt = now;
    this.expiresAt = expiresAt;
  }
}
