package ro.usv.rrm.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Setările aplicației din secțiunea {@code rrm} a fișierului {@code application.yml}.
 *
 * @param apiPrefix prefixul tuturor endpoint-urilor REST (ex. {@code /api/v1})
 * @param cors setările CORS pentru frontend
 */
@ConfigurationProperties("rrm")
public record RrmProperties(String apiPrefix, Cors cors) {

  /**
   * Setările CORS.
   *
   * @param allowedOrigins originile din care browserul poate apela API-ul
   */
  public record Cors(List<String> allowedOrigins) {}
}
