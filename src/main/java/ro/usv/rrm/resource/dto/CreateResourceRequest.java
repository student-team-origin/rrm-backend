package ro.usv.rrm.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datele pentru adăugarea sau actualizarea unei resurse (schema {@code ResursaCreate} din
 * api-contract/openapi.yaml).
 *
 * @param denumire numele resursei (obligatoriu, maximum 120 de caractere)
 * @param specificatii specificațiile tehnice (opțional)
 * @param categorieId id-ul categoriei (obligatoriu)
 * @param laboratorId id-ul laboratorului (obligatoriu)
 * @param timpPregatireMin timpul de pregătire în minute (opțional, implicit 0)
 * @param reguliAcces regulile de acces (opțional)
 */
public record CreateResourceRequest(
    @NotBlank(message = "Denumirea este obligatorie.")
        @Size(max = 120, message = "Denumirea poate avea cel mult 120 de caractere.")
        String denumire,
    String specificatii,
    @NotNull(message = "Categoria este obligatorie.") Long categorieId,
    @NotNull(message = "Laboratorul este obligatoriu.") Long laboratorId,
    // Coloana timp_pregatire_min este smallint.
    @PositiveOrZero(message = "Timpul de pregătire nu poate fi negativ.")
        @Max(value = Short.MAX_VALUE, message = "Timpul de pregătire este prea mare.")
        Integer timpPregatireMin,
    String reguliAcces) {

  /** Timpul de pregătire, cu valoarea implicită din contract (0) dacă lipsește. */
  public int preparationTimeOrDefault() {
    return timpPregatireMin == null ? 0 : timpPregatireMin;
  }
}
