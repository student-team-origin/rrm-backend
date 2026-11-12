package ro.usv.rrm.resource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Verifică filtrele catalogului și interogarea nativă pe datele de test din migrarea V2 (6 resurse:
 * 1–3 în Laborator Robotică, 4–6 în Laborator Electronică; resursa 6 în mentenanță).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResourceRepositoryTest {

  private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("id"));

  @Autowired private ResourceRepository resourceRepository;

  @Test
  void findAll_noFilters_returnsWholeCatalog() {
    Page<Resource> page = search(new ResourceFilter(null, null, null, null));

    assertThat(page.getTotalElements()).isEqualTo(6);
  }

  @Test
  void findAll_textInSpecificationsWithOtherCase_matches() {
    // „Universal Robots” apare doar în specificațiile resursei 1.
    Page<Resource> page = search(new ResourceFilter("UNIVERSAL robots", null, null, null));

    assertThat(page.getContent()).extracting(Resource::getId).containsExactly(1L);
  }

  @Test
  void findAll_textInName_matches() {
    Page<Resource> page = search(new ResourceFilter("osciloscop", null, null, null));

    assertThat(page.getContent()).extracting(Resource::getId).containsExactly(4L);
  }

  @Test
  void findAll_combinedFilters_matchAll() {
    // Categoria 2 (Echipament) are resursele 1, 4, 5, 6; doar 4 și 5 sunt active în laboratorul 2.
    Page<Resource> page = search(new ResourceFilter(null, 2L, 2L, ResourceStatus.ACTIVE));

    assertThat(page.getContent()).extracting(Resource::getId).containsExactly(4L, 5L);
  }

  @Test
  void findAll_statusFilter_usesDatabaseValue() {
    Page<Resource> page = search(new ResourceFilter(null, null, null, ResourceStatus.MAINTENANCE));

    assertThat(page.getContent()).extracting(Resource::getId).containsExactly(6L);
  }

  @Test
  void findAll_likeWildcardInText_isMatchedLiterally() {
    Page<Resource> page = search(new ResourceFilter("%", null, null, null));

    assertThat(page.getTotalElements()).isZero();
  }

  @Test
  void hasReservations_reflectsReservationTable() {
    assertThat(resourceRepository.hasReservations(1L)).isTrue();
    assertThat(resourceRepository.hasReservations(3L)).isFalse();
  }

  private Page<Resource> search(ResourceFilter filter) {
    return resourceRepository.findAll(ResourceSpecifications.matching(filter), FIRST_PAGE);
  }
}
