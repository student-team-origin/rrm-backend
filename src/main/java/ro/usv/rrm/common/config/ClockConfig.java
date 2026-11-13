package ro.usv.rrm.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ceasul aplicației, injectat acolo unde contează ora curentă, ca să poată fi fixat în teste. */
@Configuration
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
