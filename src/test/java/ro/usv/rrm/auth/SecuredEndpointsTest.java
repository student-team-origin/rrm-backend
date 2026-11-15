package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Convenția RBAC: orice endpoint care modifică date (POST, PUT, PATCH, DELETE) are
 * {@code @PreAuthorize}, ca o regulă de acces să nu fie uitată la un endpoint nou.
 *
 * <p>Excepțiile sunt doar endpoint-urile publice sau cele al căror drept se verifică în Service pe
 * datele cererii (ex. „doar solicitantul își anulează rezervarea”). O excepție nouă se adaugă aici
 * explicit, cu motivul ei.
 */
@SpringBootTest
class SecuredEndpointsTest {

  private static final Set<RequestMethod> WRITE_METHODS =
      Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

  /** Metodele de controller exceptate, cu motivul. */
  private static final Set<String> EXCEPTIONS =
      Set.of(
          // Public: nu există încă un utilizator autentificat.
          "AuthController.login",
          // Orice utilizator autentificat își închide propria sesiune.
          "AuthController.logout",
          // Doar solicitantul: verificat în ReservationService.cancel (403
          // REZERVARE_ALTUI_UTILIZATOR).
          "ReservationController.cancelReservation");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void everyWriteEndpoint_hasPreAuthorizeOrIsAnExplicitException() {
    List<String> unprotected =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> isWrite(entry.getKey()))
            .map(entry -> entry.getValue())
            .filter(handler -> handler.getBeanType().getPackageName().startsWith("ro.usv.rrm"))
            .filter(handler -> !handler.hasMethodAnnotation(PreAuthorize.class))
            .map(SecuredEndpointsTest::name)
            .filter(name -> !EXCEPTIONS.contains(name))
            .sorted()
            .toList();

    assertThat(unprotected)
        .as("Endpoint-uri de scriere fără @PreAuthorize (adaugă regula sau o excepție motivată)")
        .isEmpty();
  }

  private static boolean isWrite(RequestMappingInfo mapping) {
    return mapping.getMethodsCondition().getMethods().stream().anyMatch(WRITE_METHODS::contains);
  }

  private static String name(HandlerMethod handler) {
    return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
  }
}
