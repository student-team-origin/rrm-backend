package ro.usv.rrm.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepție de domeniu transformată de {@link GlobalExceptionHandler} într-un răspuns {@link
 * ApiError} cu statusul HTTP dat.
 *
 * <p>Se creează prin metodele de fabrică, cu un cod stabil (în română, {@code UPPER_SNAKE_CASE}) și
 * un mesaj pentru utilizator:
 *
 * <pre>{@code
 * throw ApiException.notFound("RESURSA_INEXISTENTA", "Resursa cu id-ul " + id + " nu există.");
 * }</pre>
 */
@Getter
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  /** 401 – utilizatorul nu este autentificat (credențiale greșite, token invalid sau expirat). */
  public static ApiException unauthorized(String code, String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
  }

  /** 404 – resursa căutată nu există (ex. id inexistent). */
  public static ApiException notFound(String code, String message) {
    return new ApiException(HttpStatus.NOT_FOUND, code, message);
  }

  /** 409 – operația intră în conflict cu starea curentă (ex. interval ocupat, ștergere blocată). */
  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  /** 422 – o regulă de business este încălcată (ex. sold de ore insuficient). */
  public static ApiException businessRule(String code, String message) {
    return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
  }
}
