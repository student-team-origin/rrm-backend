package ro.usv.rrm.common.error;

/**
 * Operația intră în conflict cu starea curentă (ex. interval ocupat, ștergere blocată). Tratată în
 * {@link GlobalExceptionHandler} ca 409 Conflict.
 *
 * <p>Modulele de domeniu o pot folosi direct sau o pot extinde cu o excepție proprie.
 */
public class ConflictException extends ApiException {

  public ConflictException(String code, String message) {
    super(code, message);
  }
}
