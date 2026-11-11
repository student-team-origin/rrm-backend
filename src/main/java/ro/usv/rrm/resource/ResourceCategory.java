package ro.usv.rrm.resource;

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

/** Categoria unei resurse (ex. Echipament, Robot educațional, Spațiu de laborator). */
@Entity
@Table(name = "categorie_resursa")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(name = "denumire", nullable = false, unique = true, length = 60)
  private String name;

  public ResourceCategory(String name) {
    this.name = name;
  }
}
