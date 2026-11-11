package ro.usv.rrm.laboratory;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.laboratory.dto.LaboratoryResponse;

/** Citirea laboratoarelor, folosite ca nomenclator în formularele și filtrele de resurse. */
@Service
@RequiredArgsConstructor
public class LaboratoryService {

  private final LaboratoryRepository laboratoryRepository;

  /** Întoarce toate laboratoarele, ordonate după denumire. */
  @Transactional(readOnly = true)
  public List<LaboratoryResponse> findAll() {
    return laboratoryRepository.findAll(Sort.by("name")).stream()
        .map(LaboratoryResponse::from)
        .toList();
  }

  /**
   * Întoarce laboratorul cu id-ul dat.
   *
   * @throws ApiException 404 {@code LABORATOR_INEXISTENT} dacă laboratorul nu există
   */
  @Transactional(readOnly = true)
  public LaboratoryResponse findById(Long id) {
    return LaboratoryResponse.from(getLaboratory(id));
  }

  /**
   * Întoarce entitatea laboratorului cu id-ul dat, pentru Service-urile din alte module (de exemplu
   * la crearea unei resurse).
   *
   * @throws ApiException 404 {@code LABORATOR_INEXISTENT} dacă laboratorul nu există
   */
  @Transactional(readOnly = true)
  public Laboratory getLaboratory(Long id) {
    return laboratoryRepository
        .findById(id)
        .orElseThrow(() ->
            ApiException.notFound(
                "LABORATOR_INEXISTENT", "Laboratorul cu id-ul " + id + " nu există."));
  }
}
