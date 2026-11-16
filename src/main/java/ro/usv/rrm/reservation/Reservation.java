package ro.usv.rrm.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.user.User;

/**
 * O cerere de rezervare a unei resurse, pe orele unui grant (tabela {@code rezervare}).
 *
 * <p>Coloana {@code sfarsit_blocat} (sfârșitul + timpul de pregătire al resursei) doar se citește:
 * o calculează triggerul {@code trg_rezervare_sfarsit_blocat}, iar constrângerea {@code
 * rezervare_fara_suprapunere} o folosește doar pentru rezervările active. Astfel, o rezervare
 * anulată eliberează automat intervalul.
 */
@Entity
@Table(name = "rezervare")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "solicitant_id", nullable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private User requester;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resursa_id", nullable = false)
  private Resource resource;

  /**
   * Id-ul grantului pe ale cărui ore se face rezervarea. Se înlocuiește cu o asociere cu entitatea
   * grantului când este mapat modulul de granturi.
   */
  @Column(name = "grant_id", nullable = false)
  private Long grantId;

  @Column(name = "stare", nullable = false, length = 30)
  private ReservationStatus status = ReservationStatus.PENDING_COORDINATOR;

  @Column(name = "start", nullable = false)
  private Instant start;

  @Column(name = "sfarsit", nullable = false)
  private Instant end;

  /**
   * Sfârșitul intervalului blocat (sfârșitul + timpul de pregătire al resursei). Îl calculează
   * triggerul la inserare și la schimbarea resursei sau a sfârșitului; nu se scrie din cod.
   */
  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "sfarsit_blocat", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Instant blockedEnd;

  @Column(name = "ore_solicitate", nullable = false, precision = 5, scale = 2)
  private BigDecimal requestedHours;

  @Column(name = "scop", nullable = false, columnDefinition = "text")
  private String purpose;

  /** Completat de baza de date ({@code DEFAULT now()}) la inserare. */
  @Generated
  @Column(name = "data_creare", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Instant createdAt;

  @Column(name = "data_check_in")
  private Instant checkInAt;

  public Reservation(
      User requester,
      Resource resource,
      Long grantId,
      Instant start,
      Instant end,
      BigDecimal requestedHours,
      String purpose) {
    this.requester = requester;
    this.resource = resource;
    this.grantId = grantId;
    this.start = start;
    this.end = end;
    this.requestedHours = requestedHours;
    this.purpose = purpose;
  }

  /** {@code true} dacă rezervarea a fost cerută de utilizatorul dat. */
  public boolean isRequestedBy(long userId) {
    return requester.getId() == userId;
  }
}
