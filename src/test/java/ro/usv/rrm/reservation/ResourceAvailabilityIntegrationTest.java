package ro.usv.rrm.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilitatea resurselor cap-coadă (TO-41), pe datele de test din V2. Criteriul de acceptare:
 * intervalele ocupate întoarse corespund rezervărilor active din baza de date.
 *
 * <p>Pe resursa 1 (Braț robotic UR5, 15 minute de pregătire), în jurul rezervării 2 (Aprobata) se
 * află și rezervările 1 (Finalizata), 6 (AnulataAutomat) și 7 (Anulata, cu același interval ca 2):
 * acestea nu ocupă resursa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResourceAvailabilityIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void getAvailability_matchesActiveReservationsInDatabase() throws Exception {
    String token = login();
    // Din ziua dinaintea rezervării 1 până după rezervarea 2: cuprinde toate rezervările resursei
    // 1.
    OffsetDateTime from = timeOf("start", 1).minusDays(1);
    OffsetDateTime to = timeOf("sfarsit", 2).plusDays(1);

    String body =
        mockMvc
            .perform(
                get("/api/v1/resources/1/availability")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .param("from", from.toString())
                    .param("to", to.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> active =
        jdbc.queryForList(
            "SELECT start, sfarsit_blocat, stare FROM rezervare WHERE resursa_id = 1"
                + " AND stare IN ('InAsteptareCoordonator', 'InAsteptareLaborator', 'Aprobata',"
                + " 'Activa') AND start < ? AND sfarsit_blocat > ? ORDER BY start",
            to,
            from);
    List<Map<String, Object>> returned = JsonPath.read(body, "$");
    assertThat(returned).hasSameSizeAs(active).isNotEmpty();
    for (int i = 0; i < active.size(); i++) {
      assertThat(Instant.parse((String) returned.get(i).get("start")))
          .isEqualTo(toInstant(active.get(i).get("start")));
      assertThat(Instant.parse((String) returned.get(i).get("sfarsit")))
          .isEqualTo(toInstant(active.get(i).get("sfarsit_blocat")));
      assertThat(returned.get(i).get("stare")).isEqualTo(active.get(i).get("stare"));
    }
    // Rezervarea 2 (Aprobata), cu sfârșitul prelungit cu cele 15 minute de pregătire.
    assertThat(returned.getFirst().get("stare")).isEqualTo("Aprobata");
    assertThat(Instant.parse((String) returned.getFirst().get("sfarsit")))
        .isEqualTo(timeOf("sfarsit", 2).plusMinutes(15).toInstant());
  }

  @Test
  void getAvailability_unknownResource_returns404() throws Exception {
    String token = login();

    mockMvc
        .perform(
            get("/api/v1/resources/999999/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("from", "2026-11-16T00:00:00+02:00")
                .param("to", "2026-11-23T00:00:00+02:00"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESURSA_INEXISTENTA"));
  }

  @Test
  void getAvailability_rangeLongerThanMaximum_returns400() throws Exception {
    String token = login();

    mockMvc
        .perform(
            get("/api/v1/resources/1/availability")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("from", "2026-11-01T00:00:00+02:00")
                .param("to", "2027-03-01T00:00:00+02:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INTERVAL_INVALID"));
  }

  private OffsetDateTime timeOf(String column, long reservationId) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM rezervare WHERE id = ?", OffsetDateTime.class, reservationId);
  }

  private static Instant toInstant(Object timestamp) {
    return timestamp instanceof OffsetDateTime offset
        ? offset.toInstant()
        : ((Timestamp) timestamp).toInstant();
  }

  private String login() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "andrei.popescu@student.usv.ro", "parola": "parola123"}
                        """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.tokens.accessToken");
  }
}
