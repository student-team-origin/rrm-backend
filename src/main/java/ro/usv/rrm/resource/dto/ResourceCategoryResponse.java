package ro.usv.rrm.resource.dto;

import ro.usv.rrm.resource.ResourceCategory;

/**
 * O categorie de resurse (schema {@code CategorieResursa} din api-contract/openapi.yaml).
 *
 * @param id id-ul categoriei
 * @param denumire numele categoriei
 */
public record ResourceCategoryResponse(Long id, String denumire) {

  public static ResourceCategoryResponse from(ResourceCategory category) {
    return new ResourceCategoryResponse(category.getId(), category.getName());
  }
}
