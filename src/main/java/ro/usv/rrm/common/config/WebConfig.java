package ro.usv.rrm.common.config;

import org.springframework.beans.factory.annotation.Value;
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
public class WebConfig implements WebMvcConfigurer {

  private final String apiPrefix;

  public WebConfig(@Value("${rrm.api-prefix}") String apiPrefix) {
    this.apiPrefix = apiPrefix;
  }

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(apiPrefix, HandlerTypePredicate.forAnnotation(RestController.class));
  }
}
