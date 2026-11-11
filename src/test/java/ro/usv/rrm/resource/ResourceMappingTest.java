package ro.usv.rrm.resource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import ro.usv.rrm.laboratory.Laboratory;

/**
 * Verifică maparea entităților {@link Resource}, {@link ResourceCategory} și {@link Laboratory} pe
 * baza de date locală creată de Flyway. Pornirea contextului validează deja schema ({@code
 * ddl-auto: validate}); testele de aici verifică citirea, scrierea și conversia stării.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResourceMappingTest {

  @Autowired private TestEntityManager em;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void find_seededResource_mapsColumnsAndAssociations() {
    Resource resource = em.find(Resource.class, 1L);

    assertThat(resource.getName()).isEqualTo("Braț robotic UR5");
    assertThat(resource.getStatus()).isEqualTo(ResourceStatus.ACTIVE);
    assertThat(resource.getPreparationTimeMinutes()).isEqualTo(15);
    assertThat(resource.getLaboratory().getName()).isEqualTo("Laborator Robotică");
    assertThat(resource.getCategory().getName()).isEqualTo("Echipament");
  }

  @Test
  void persist_newResource_isStoredWithDatabaseValues() {
    Laboratory laboratory = em.find(Laboratory.class, 1L);
    ResourceCategory category = em.find(ResourceCategory.class, 1L);
    Resource resource = new Resource(laboratory, category, "Imprimantă 3D");
    resource.setSpecifications("Volum 25x21x21 cm");
    resource.setStatus(ResourceStatus.MAINTENANCE);
    resource.setPreparationTimeMinutes(20);

    Long id = em.persistAndFlush(resource).getId();
    em.clear();

    assertThat(id).isNotNull();
    String storedStatus =
        jdbc.queryForObject("SELECT stare FROM resursa WHERE id = ?", String.class, id);
    assertThat(storedStatus).isEqualTo("Mentenanta");
    Resource reloaded = em.find(Resource.class, id);
    assertThat(reloaded.getStatus()).isEqualTo(ResourceStatus.MAINTENANCE);
    assertThat(reloaded.getPreparationTimeMinutes()).isEqualTo(20);
    assertThat(reloaded.getLaboratory().getId()).isEqualTo(laboratory.getId());
  }
}
