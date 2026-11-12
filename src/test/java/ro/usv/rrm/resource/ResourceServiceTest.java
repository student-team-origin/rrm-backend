package ro.usv.rrm.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.laboratory.Laboratory;
import ro.usv.rrm.laboratory.LaboratoryService;
import ro.usv.rrm.resource.dto.ChangeResourceStatusRequest;
import ro.usv.rrm.resource.dto.CreateResourceRequest;
import ro.usv.rrm.resource.dto.ResourceResponse;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

  @Mock private ResourceRepository resourceRepository;
  @Mock private ResourceCategoryService categoryService;
  @Mock private LaboratoryService laboratoryService;

  @InjectMocks private ResourceService resourceService;

  private Laboratory laboratory;
  private ResourceCategory category;

  @BeforeEach
  void setUp() {
    laboratory = withId(new Laboratory("Laborator Robotică", "Corp C, sala C204", 2L), 1L);
    category = withId(new ResourceCategory("Echipament"), 2L);
  }

  @Test
  void create_validRequest_savesActiveResource() {
    given(laboratoryService.getLaboratory(1L)).willReturn(laboratory);
    given(categoryService.getCategory(2L)).willReturn(category);
    given(resourceRepository.save(any(Resource.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 10L));

    ResourceResponse response =
        resourceService.create(
            new CreateResourceRequest(
                "Imprimantă 3D", "Volum 25 cm", 2L, 1L, 20, "Doar cu instruire"));

    assertThat(response.id()).isEqualTo(10L);
    assertThat(response.denumire()).isEqualTo("Imprimantă 3D");
    assertThat(response.specificatii()).isEqualTo("Volum 25 cm");
    assertThat(response.stare()).isEqualTo(ResourceStatus.ACTIVE);
    assertThat(response.timpPregatireMin()).isEqualTo(20);
    assertThat(response.reguliAcces()).isEqualTo("Doar cu instruire");
    assertThat(response.categorie().id()).isEqualTo(2L);
    assertThat(response.categorie().denumire()).isEqualTo("Echipament");
    assertThat(response.laboratorId()).isEqualTo(1L);
  }

  @Test
  void create_missingPreparationTime_defaultsToZero() {
    given(laboratoryService.getLaboratory(1L)).willReturn(laboratory);
    given(categoryService.getCategory(2L)).willReturn(category);
    given(resourceRepository.save(any(Resource.class))).willAnswer(i -> i.getArgument(0));

    ResourceResponse response =
        resourceService.create(new CreateResourceRequest("Osciloscop", null, 2L, 1L, null, null));

    assertThat(response.timpPregatireMin()).isZero();
  }

  @Test
  void create_unknownCategory_throwsNotFound() {
    given(laboratoryService.getLaboratory(1L)).willReturn(laboratory);
    given(categoryService.getCategory(99L))
        .willThrow(ApiException.notFound("CATEGORIE_INEXISTENTA", "Categoria nu există."));

    assertThatThrownBy(() ->
            resourceService.create(new CreateResourceRequest("Osciloscop", null, 99L, 1L, 0, null)))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "CATEGORIE_INEXISTENTA"));
    then(resourceRepository).should(never()).save(any());
  }

  @Test
  void create_unknownLaboratory_throwsNotFound() {
    given(laboratoryService.getLaboratory(99L))
        .willThrow(ApiException.notFound("LABORATOR_INEXISTENT", "Laboratorul nu există."));

    assertThatThrownBy(() ->
            resourceService.create(new CreateResourceRequest("Osciloscop", null, 2L, 99L, 0, null)))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "LABORATOR_INEXISTENT"));
    then(resourceRepository).should(never()).save(any());
  }

  @Test
  void update_existingResource_changesFieldsButKeepsStatus() {
    Resource resource = existingResource(ResourceStatus.MAINTENANCE);
    ResourceCategory newCategory = withId(new ResourceCategory("Robot educațional"), 1L);
    Laboratory newLaboratory =
        withId(new Laboratory("Laborator Electronică", "Corp C, sala C201", 3L), 2L);
    given(resourceRepository.findById(5L)).willReturn(Optional.of(resource));
    given(laboratoryService.getLaboratory(2L)).willReturn(newLaboratory);
    given(categoryService.getCategory(1L)).willReturn(newCategory);

    ResourceResponse response =
        resourceService.update(
            5L, new CreateResourceRequest("Robot NAO v6", "25 DOF", 1L, 2L, 10, null));

    assertThat(response.denumire()).isEqualTo("Robot NAO v6");
    assertThat(response.specificatii()).isEqualTo("25 DOF");
    assertThat(response.timpPregatireMin()).isEqualTo(10);
    assertThat(response.categorie().id()).isEqualTo(1L);
    assertThat(response.laboratorId()).isEqualTo(2L);
    assertThat(response.stare()).isEqualTo(ResourceStatus.MAINTENANCE);
  }

  @Test
  void update_unknownResource_throwsNotFound() {
    given(resourceRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() ->
            resourceService.update(
                99L, new CreateResourceRequest("Osciloscop", null, 2L, 1L, 0, null)))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "RESURSA_INEXISTENTA"));
  }

  @Test
  void findById_unknownResource_throwsNotFound() {
    given(resourceRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> resourceService.findById(99L))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "RESURSA_INEXISTENTA"));
  }

  @Test
  void delete_resourceWithReservations_throwsConflict() {
    Resource resource = existingResource(ResourceStatus.ACTIVE);
    given(resourceRepository.findById(5L)).willReturn(Optional.of(resource));
    given(resourceRepository.hasReservations(5L)).willReturn(true);

    assertThatThrownBy(() -> resourceService.delete(5L))
        .satisfies(ex -> assertApiError(ex, HttpStatus.CONFLICT, "RESURSA_ARE_REZERVARI"));
    then(resourceRepository).should(never()).delete(any(Resource.class));
  }

  @Test
  void delete_resourceWithoutReservations_deletesIt() {
    Resource resource = existingResource(ResourceStatus.ACTIVE);
    given(resourceRepository.findById(5L)).willReturn(Optional.of(resource));
    given(resourceRepository.hasReservations(5L)).willReturn(false);

    resourceService.delete(5L);

    then(resourceRepository).should().delete(resource);
  }

  @Test
  void delete_unknownResource_throwsNotFound() {
    given(resourceRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> resourceService.delete(99L))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "RESURSA_INEXISTENTA"));
  }

  @Test
  void changeStatus_existingResource_updatesStatusWithoutCancellations() {
    Resource resource = existingResource(ResourceStatus.ACTIVE);
    given(resourceRepository.findById(5L)).willReturn(Optional.of(resource));

    var response =
        resourceService.changeStatus(
            5L, new ChangeResourceStatusRequest(ResourceStatus.MAINTENANCE, "Calibrare anuală"));

    assertThat(resource.getStatus()).isEqualTo(ResourceStatus.MAINTENANCE);
    assertThat(response.resursa().stare()).isEqualTo(ResourceStatus.MAINTENANCE);
    assertThat(response.rezervariAnulate()).isZero();
  }

  @Test
  void changeStatus_unknownResource_throwsNotFound() {
    given(resourceRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() ->
            resourceService.changeStatus(
                99L, new ChangeResourceStatusRequest(ResourceStatus.BROKEN, null)))
        .satisfies(ex -> assertApiError(ex, HttpStatus.NOT_FOUND, "RESURSA_INEXISTENTA"));
  }

  private Resource existingResource(ResourceStatus status) {
    Resource resource = withId(new Resource(laboratory, category, "Robot NAO"), 5L);
    resource.setStatus(status);
    return resource;
  }

  /** Id-ul este generat de baza de date și nu are setter; în testele unitare îl setăm direct. */
  private static <T> T withId(T entity, Long id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static void assertApiError(Throwable ex, HttpStatus status, String code) {
    assertThat(ex).isInstanceOf(ApiException.class);
    ApiException apiException = (ApiException) ex;
    assertThat(apiException.getStatus()).isEqualTo(status);
    assertThat(apiException.getCode()).isEqualTo(code);
  }
}
