package ro.usv.rrm.common.error;

/**
 * Resursa căutată nu există (ex. id inexistent). Tratată în {@link GlobalExceptionHandler} ca 404
 * Not Found.
 *
 * <p>Modulele de domeniu o pot folosi direct sau o pot extinde cu o excepție proprie.
 */
public class NotFoundException extends ApiException {

  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
