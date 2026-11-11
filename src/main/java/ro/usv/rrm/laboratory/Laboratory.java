package ro.usv.rrm.laboratory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un laborator de cercetare, cu un responsabil care validează rezervările resurselor sale. */
@Entity
@Table(name = "laborator")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Laboratory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(name = "denumire", nullable = false, length = 80)
  private String name;

  @Column(name = "locatie", nullable = false, length = 80)
  private String location;

  /**
   * Id-ul utilizatorului responsabil. Se înlocuiește cu o asociere {@code @ManyToOne User} când
   * este mapat modulul de utilizatori.
   */
  @Column(name = "responsabil_id", nullable = false)
  private Long responsibleId;

  public Laboratory(String name, String location, Long responsibleId) {
    this.name = name;
    this.location = location;
    this.responsibleId = responsibleId;
  }
}
