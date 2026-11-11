package ro.usv.rrm.resource.dto;

import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.resource.ResourceStatus;

/**
 * O resursă din catalog (schema {@code Resursa} din api-contract/openapi.yaml).
 *
 * <p>Se construiește în Service, în tranzacție, pentru că asocierea cu categoria este LAZY.
 *
 * @param id id-ul resursei
 * @param denumire numele resursei
 * @param specificatii specificațiile tehnice
 * @param stare starea logică (în JSON: {@code Activa}, {@code Mentenanta}, {@code Defecta})
 * @param timpPregatireMin timpul de pregătire adăugat după fiecare rezervare, în minute
 * @param reguliAcces regulile de acces
 * @param categorie categoria resursei
 * @param laboratorId id-ul laboratorului căruia îi aparține resursa
 */
public record ResourceResponse(
    Long id,
    String denumire,
    String specificatii,
    ResourceStatus stare,
    int timpPregatireMin,
    String reguliAcces,
    ResourceCategoryResponse categorie,
    Long laboratorId) {

  public static ResourceResponse from(Resource resource) {
    return new ResourceResponse(
        resource.getId(),
        resource.getName(),
        resource.getSpecifications(),
        resource.getStatus(),
        resource.getPreparationTimeMinutes(),
        resource.getAccessRules(),
        ResourceCategoryResponse.from(resource.getCategory()),
        resource.getLaboratory().getId());
  }
}
