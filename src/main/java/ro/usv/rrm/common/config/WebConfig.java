package ro.usv.rrm.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Adaugă prefixul comun (implicit {@code /api/v1}) tuturor controllerelor REST.
 *
 * <p>Astfel, un controller declară doar calea resursei, de exemplu
 * {@code @RequestMapping("/resources")}, iar endpoint-ul final devine {@code /api/v1/resources}.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final RrmProperties properties;

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
        properties.apiPrefix(), HandlerTypePredicate.forAnnotation(RestController.class));
  }
}
