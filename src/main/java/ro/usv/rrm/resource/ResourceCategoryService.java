package ro.usv.rrm.resource;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;

/** Citirea categoriilor de resurse, folosite ca nomenclator în formulare și filtre. */
@Service
@RequiredArgsConstructor
public class ResourceCategoryService {

  private final ResourceCategoryRepository categoryRepository;

  /** Întoarce toate categoriile de resurse, ordonate după denumire. */
  @Transactional(readOnly = true)
  public List<ResourceCategoryResponse> findAll() {
    return categoryRepository.findAll(Sort.by("name")).stream()
        .map(ResourceCategoryResponse::from)
        .toList();
  }

  /**
   * Întoarce entitatea categoriei cu id-ul dat (de exemplu la crearea unei resurse).
   *
   * @throws ApiException 404 {@code CATEGORIE_INEXISTENTA} dacă categoria nu există
   */
  @Transactional(readOnly = true)
  public ResourceCategory getCategory(Long id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() ->
            ApiException.notFound(
                "CATEGORIE_INEXISTENTA", "Categoria cu id-ul " + id + " nu există."));
  }
}
