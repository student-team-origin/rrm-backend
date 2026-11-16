package ro.usv.rrm.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Corpul răspunsurilor de eroare (schemele {@code Error} și {@code ConflictInterval} din
 * api-contract/openapi.yaml).
 *
 * @param code cod stabil, citit de frontend (ex. {@code RESURSA_INEXISTENTA})
 * @param message mesaj în română, afișabil utilizatorului
 * @param fields erorile de validare pe câmpuri (doar pentru 400), altfel absent
 * @param intervaleLibere intervalele libere propuse în locul celui ocupat (doar pentru 409 {@code
 *     INTERVAL_OCUPAT}), altfel absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code, String message, Map<String, String> fields, List<FreeInterval> intervaleLibere) {

  public ApiError(String code, String message, Map<String, String> fields) {
    this(code, message, fields, null);
  }

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null, null);
  }

  /**
   * Un interval liber al unei resurse.
   *
   * @param start începutul intervalului
   * @param sfarsit sfârșitul intervalului
   */
  public record FreeInterval(Instant start, Instant sfarsit) {}
}
