package ro.usv.rrm.approval;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.user.User;

/**
 * Decizia unui aprobator asupra unei rezervări (tabela {@code aprobare}). Fluxul are 2 niveluri: 1
 * = Coordonatorul grantului, 2 = Responsabilul laboratorului.
 */
@Entity
@Table(name = "aprobare")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Approval {

  /** Nivelul 1: Coordonatorul grantului. */
  public static final int COORDINATOR_LEVEL = 1;

  /** Nivelul 2: Responsabilul laboratorului resursei. */
  public static final int LABORATORY_LEVEL = 2;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rezervare_id", nullable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Reservation reservation;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "nivel", nullable = false)
  private int level;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aprobator_id", nullable = false)
  private User approver;

  @Column(name = "decizie", nullable = false, length = 10)
  private ApprovalDecision decision;

  /** Obligatorie la respingere (CHECK în baza de date). */
  @Column(name = "observatie", columnDefinition = "text")
  private String observation;

  /** Completat de baza de date ({@code DEFAULT now()}) la inserare. */
  @Generated
  @Column(name = "data_decizie", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Instant decidedAt;

  public Approval(Reservation reservation, int level, User approver, ApprovalDecision decision) {
    this.reservation = reservation;
    this.level = level;
    this.approver = approver;
    this.decision = decision;
  }
}
