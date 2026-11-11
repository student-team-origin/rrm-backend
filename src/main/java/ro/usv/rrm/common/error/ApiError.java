package ro.usv.rrm.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Corpul răspunsurilor de eroare (schema {@code Error} din docs/openapi.yaml).
 *
 * @param code cod stabil, citit de frontend (ex. {@code RESURSA_INEXISTENTA})
 * @param message mesaj în română, afișabil utilizatorului
 * @param fields erorile de validare pe câmpuri (doar pentru 400), altfel absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, String> fields) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }
}
