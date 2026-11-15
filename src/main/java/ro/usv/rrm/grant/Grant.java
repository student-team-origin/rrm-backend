package ro.usv.rrm.grant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un grant de cercetare, cu orele alocate pentru rezervări (tabela {@code grant_cercetare}).
 *
 * <p>Orele consumate și rămase nu se păstrează aici: le calculează view-ul {@code v_sold_grant} din
 * rezervările grantului (vezi {@link GrantRepository}).
 */
@Entity
@Table(name = "grant_cercetare")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(name = "cod", nullable = false, unique = true, length = 40)
  private String code;

  /**
   * Coordonatorul afișat al grantului. Drepturile de coordonator (vizibilitate, aprobări) vin însă
   * din rolul {@code CoordonatorGrant} cu scopul grantului, din {@code utilizator_rol}.
   */
  // TODO: tichetul de administrare a granturilor - la schimbarea coordonatorului se actualizează în
  // aceeași tranzacție și rolul CoordonatorGrant din utilizator_rol (test pentru sincronizare).
  @Column(name = "coordonator_id", nullable = false)
  private Long coordinatorId;

  @Column(name = "ore_alocate", nullable = false, precision = 7, scale = 2)
  private BigDecimal allocatedHours;

  @Column(name = "data_start", nullable = false)
  private LocalDate startDate;

  @Column(name = "data_sfarsit", nullable = false)
  private LocalDate endDate;
}
