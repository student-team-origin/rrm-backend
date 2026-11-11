package ro.usv.rrm.resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.laboratory.LaboratoryService;
import ro.usv.rrm.resource.dto.ChangeResourceStatusRequest;
import ro.usv.rrm.resource.dto.ChangeResourceStatusResponse;
import ro.usv.rrm.resource.dto.CreateResourceRequest;
import ro.usv.rrm.resource.dto.ResourceResponse;

/** Catalogul de resurse: căutare, adăugare, actualizare, ștergere și schimbarea stării. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

  static final int MAX_PAGE_SIZE = 100;

  private final ResourceRepository resourceRepository;
  private final ResourceCategoryService categoryService;
  private final LaboratoryService laboratoryService;

  /**
   * Caută în catalog după filtrele date, ordonat după denumire.
   *
   * @param page numărul paginii, de la 0
   * @param size dimensiunea paginii, între 1 și {@value #MAX_PAGE_SIZE}
   */
  @Transactional(readOnly = true)
  public PageResponse<ResourceResponse> search(ResourceFilter filter, int page, int size) {
    PageRequest pageRequest =
        PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by("name").and(Sort.by("id")));
    return PageResponse.from(
        resourceRepository.findAll(ResourceSpecifications.matching(filter), pageRequest),
        ResourceResponse::from);
  }

  /**
   * Întoarce detaliile unei resurse.
   *
   * @throws ApiException 404 {@code RESURSA_INEXISTENTA}
   */
  @Transactional(readOnly = true)
  public ResourceResponse findById(Long id) {
    return ResourceResponse.from(getResource(id));
  }

  /**
   * Adaugă o resursă în catalog, în starea {@code Activa}.
   *
   * @throws ApiException 404 {@code CATEGORIE_INEXISTENTA} sau {@code LABORATOR_INEXISTENT}
   */
  @Transactional
  public ResourceResponse create(CreateResourceRequest request) {
    Resource resource =
        new Resource(
            laboratoryService.getLaboratory(request.laboratorId()),
            categoryService.getCategory(request.categorieId()),
            request.denumire());
    applyDetails(resource, request);
    return ResourceResponse.from(resourceRepository.save(resource));
  }

  /**
   * Actualizează datele unei resurse. Starea nu se schimbă aici, ci prin {@link #changeStatus}.
   *
   * @throws ApiException 404 {@code RESURSA_INEXISTENTA}, {@code CATEGORIE_INEXISTENTA} sau {@code
   *     LABORATOR_INEXISTENT}
   */
  @Transactional
  public ResourceResponse update(Long id, CreateResourceRequest request) {
    Resource resource = getResource(id);
    resource.setLaboratory(laboratoryService.getLaboratory(request.laboratorId()));
    resource.setCategory(categoryService.getCategory(request.categorieId()));
    resource.setName(request.denumire());
    applyDetails(resource, request);
    return ResourceResponse.from(resource);
  }

  /**
   * Șterge o resursă care nu apare în nicio rezervare. Atașamentele ei se șterg odată cu ea.
   *
   * @throws ApiException 404 {@code RESURSA_INEXISTENTA}; 409 {@code RESURSA_ARE_REZERVARI}
   */
  @Transactional
  public void delete(Long id) {
    Resource resource = getResource(id);
    if (resourceRepository.hasReservations(id)) {
      throw ApiException.conflict(
          "RESURSA_ARE_REZERVARI", "Resursa are rezervări și nu poate fi ștearsă.");
    }
    resourceRepository.delete(resource);
  }

  /**
   * Schimbă starea unei resurse (Activă / Mentenanță / Defectă).
   *
   * @throws ApiException 404 {@code RESURSA_INEXISTENTA}
   */
  @Transactional
  public ChangeResourceStatusResponse changeStatus(Long id, ChangeResourceStatusRequest request) {
    Resource resource = getResource(id);
    resource.setStatus(request.stare());
    log.info(
        "Resursa {} a trecut în starea {} (motiv: {})",
        id,
        request.stare().getValue(),
        request.motiv());
    // Anularea rezervărilor afectate și notificările vin odată cu modulele de rezervări și
    // notificări; până atunci nu se anulează nimic.
    int cancelledReservations = 0;
    return new ChangeResourceStatusResponse(ResourceResponse.from(resource), cancelledReservations);
  }

  private Resource getResource(Long id) {
    return resourceRepository
        .findById(id)
        .orElseThrow(() ->
            ApiException.notFound("RESURSA_INEXISTENTA", "Resursa cu id-ul " + id + " nu există."));
  }

  private static void applyDetails(Resource resource, CreateResourceRequest request) {
    resource.setSpecifications(request.specificatii());
    resource.setPreparationTimeMinutes(request.preparationTimeOrDefault());
    resource.setAccessRules(request.reguliAcces());
  }
}
