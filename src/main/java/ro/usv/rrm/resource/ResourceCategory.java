package ro.usv.rrm.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Categoria unei resurse (ex. Echipament, Robot educațional, Spațiu de laborator). */
@Entity
@Table(name = "categorie_resursa")
public class ResourceCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "denumire", nullable = false, unique = true, length = 60)
  private String name;

  /** Constructor cerut de JPA. */
  protected ResourceCategory() {}

  public ResourceCategory(String name) {
    this.name = name;
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
}
