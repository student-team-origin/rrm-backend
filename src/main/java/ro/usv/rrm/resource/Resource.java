package ro.usv.rrm.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ro.usv.rrm.laboratory.Laboratory;

/**
 * O resursă de cercetare care poate fi rezervată: echipament, robot, spațiu de laborator.
 *
 * <p>Aparține unui laborator și unei categorii. Timpul de pregătire se adaugă automat după fiecare
 * rezervare (de triggerul {@code trg_rezervare_sfarsit_blocat} din baza de date).
 */
@Entity
@Table(name = "resursa")
public class Resource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "laborator_id", nullable = false)
  private Laboratory laboratory;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "categorie_id", nullable = false)
  private ResourceCategory category;

  @Column(name = "denumire", nullable = false, length = 120)
  private String name;

  @Column(name = "specificatii", columnDefinition = "text")
  private String specifications;

  @Column(name = "stare", nullable = false, length = 20)
  private ResourceStatus status = ResourceStatus.ACTIVE;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "timp_pregatire_min", nullable = false)
  private int preparationTimeMinutes;

  @Column(name = "reguli_acces", columnDefinition = "text")
  private String accessRules;

  /** Constructor cerut de JPA. */
  protected Resource() {}

  public Resource(Laboratory laboratory, ResourceCategory category, String name) {
    this.laboratory = laboratory;
    this.category = category;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public Laboratory getLaboratory() {
    return laboratory;
  }

  public void setLaboratory(Laboratory laboratory) {
    this.laboratory = laboratory;
  }

  public ResourceCategory getCategory() {
    return category;
  }

  public void setCategory(ResourceCategory category) {
    this.category = category;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSpecifications() {
    return specifications;
  }

  public void setSpecifications(String specifications) {
    this.specifications = specifications;
  }

  public ResourceStatus getStatus() {
    return status;
  }

  public void setStatus(ResourceStatus status) {
    this.status = status;
  }

  public int getPreparationTimeMinutes() {
    return preparationTimeMinutes;
  }

  public void setPreparationTimeMinutes(int preparationTimeMinutes) {
    this.preparationTimeMinutes = preparationTimeMinutes;
  }

  public String getAccessRules() {
    return accessRules;
  }

  public void setAccessRules(String accessRules) {
    this.accessRules = accessRules;
  }
}
