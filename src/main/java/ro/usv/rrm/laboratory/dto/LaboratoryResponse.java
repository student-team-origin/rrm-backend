package ro.usv.rrm.laboratory.dto;

import ro.usv.rrm.laboratory.Laboratory;

/**
 * Un laborator (schema {@code Laborator} din api-contract/openapi.yaml).
 *
 * @param id id-ul laboratorului
 * @param denumire numele laboratorului
 * @param locatie clădirea și sala
 * @param responsabilId id-ul utilizatorului responsabil de laborator
 */
public record LaboratoryResponse(Long id, String denumire, String locatie, Long responsabilId) {

  public static LaboratoryResponse from(Laboratory laboratory) {
    return new LaboratoryResponse(
        laboratory.getId(),
        laboratory.getName(),
        laboratory.getLocation(),
        laboratory.getResponsibleId());
  }
}
