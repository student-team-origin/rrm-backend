package ro.usv.rrm.common.error;

/** Bază pentru excepțiile de domeniu care se transformă într-un {@link ApiError}. */
public abstract class ApiException extends RuntimeException {

  private final String code;

  protected ApiException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
