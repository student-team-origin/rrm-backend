package ro.usv.rrm.common.error;

/**
 * O regulă de business este încălcată (ex. sold de ore insuficient). Tratată în {@link
 * GlobalExceptionHandler} ca 422 Unprocessable Entity.
 *
 * <p>Modulele de domeniu o pot folosi direct sau o pot extinde cu o excepție proprie.
 */
public class BusinessRuleException extends ApiException {

  public BusinessRuleException(String code, String message) {
    super(code, message);
  }
}
