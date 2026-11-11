package ro.usv.rrm.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Transformă excepțiile în răspunsuri {@link ApiError}, cu codurile HTTP din docs/openapi.yaml. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, ex);
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> handleConflict(ConflictException ex) {
    return error(HttpStatus.CONFLICT, ex);
  }

  @ExceptionHandler(BusinessRuleException.class)
  ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
    return error(HttpStatus.UNPROCESSABLE_CONTENT, ex);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    return ResponseEntity.badRequest()
        .body(new ApiError("DATE_INVALIDE", "Datele trimise nu sunt valide.", fields));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> handleUnreadable(Exception ex) {
    return ResponseEntity.badRequest()
        .body(ApiError.of("CERERE_INVALIDA", "Cererea nu are un format valid."));
  }

  /**
   * Constrângerile din baza de date (chei străine, unicitate, excluderea suprapunerilor) sunt
   * ultima linie de apărare; dacă sunt încălcate, operația intră în conflict cu datele existente.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Constrângere încălcată în baza de date: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError.of("CONFLICT_DATE", "Operația intră în conflict cu datele existente."));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNoEndpoint(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of("ENDPOINT_INEXISTENT", "Endpoint-ul cerut nu există."));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            ApiError.of("METODA_NEPERMISA", "Metoda HTTP nu este permisă pentru acest endpoint."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    log.error("Eroare neașteptată", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiError.of("EROARE_INTERNA", "A apărut o eroare neașteptată pe server."));
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, ApiException ex) {
    return ResponseEntity.status(status).body(ApiError.of(ex.getCode(), ex.getMessage()));
  }
}
