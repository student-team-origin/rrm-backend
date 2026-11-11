package ro.usv.rrm.laboratory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un laborator de cercetare, cu un responsabil care validează rezervările resurselor sale. */
@Entity
@Table(name = "laborator")
public class Laboratory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
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

  /** Constructor cerut de JPA. */
  protected Laboratory() {}

  public Laboratory(String name, String location, Long responsibleId) {
    this.name = name;
    this.location = location;
    this.responsibleId = responsibleId;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public Long getResponsibleId() {
    return responsibleId;
  }

  public void setResponsibleId(Long responsibleId) {
    this.responsibleId = responsibleId;
  }
}
