package ro.usv.rrm.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.SessionService;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.reservation.dto.OccupiedIntervalResponse;

@WebMvcTest(ResourceAvailabilityController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class ResourceAvailabilityControllerTest {

  private static final AuthenticatedUser USER =
      new AuthenticatedUser(6, UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10"), List.of());

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReservationService reservationService;

  /** Cerut de filtrul JWT din SecurityConfig; testele pun direct utilizatorul autentificat. */
  @MockitoBean private SessionService sessionService;

  @Test
  void getAvailability_range_returnsOccupiedIntervals() throws Exception {
    given(
            reservationService.findOccupiedIntervals(
                1L, Instant.parse("2026-11-15T22:00:00Z"), Instant.parse("2026-11-22T22:00:00Z")))
        .willReturn(
            List.of(
                new OccupiedIntervalResponse(
                    Instant.parse("2026-11-16T08:00:00Z"),
                    Instant.parse("2026-11-16T10:15:00Z"),
                    ReservationStatus.APPROVED)));

    mockMvc
        .perform(
            get("/api/v1/resources/1/availability")
                .with(authentication(authenticated()))
                .param("from", "2026-11-16T00:00:00+02:00")
                .param("to", "2026-11-23T00:00:00+02:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].start").value("2026-11-16T08:00:00Z"))
        .andExpect(jsonPath("$[0].sfarsit").value("2026-11-16T10:15:00Z"))
        .andExpect(jsonPath("$[0].stare").value("Aprobata"));
  }

  @Test
  void getAvailability_missingTo_returns400WithField() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/resources/1/availability")
                .with(authentication(authenticated()))
                .param("from", "2026-11-16T00:00:00+02:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CERERE_INVALIDA"))
        .andExpect(jsonPath("$.fields.to").value("Parametrul este obligatoriu."));

    then(reservationService).should(never()).findOccupiedIntervals(anyLong(), any(), any());
  }

  @Test
  void getAvailability_fromWithoutOffset_returns400WithExpectedFormat() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/resources/1/availability")
                .with(authentication(authenticated()))
                .param("from", "2026-11-16")
                .param("to", "2026-11-23T00:00:00+02:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CERERE_INVALIDA"))
        .andExpect(jsonPath("$.fields.from").exists());
  }

  @Test
  void getAvailability_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/resources/1/availability")
                .param("from", "2026-11-16T00:00:00+02:00")
                .param("to", "2026-11-23T00:00:00+02:00"))
        .andExpect(status().isUnauthorized());
  }

  private static UsernamePasswordAuthenticationToken authenticated() {
    return new UsernamePasswordAuthenticationToken(USER, null, List.of());
  }
}
