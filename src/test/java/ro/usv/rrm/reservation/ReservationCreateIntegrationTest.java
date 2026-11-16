package ro.usv.rrm.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crearea cererilor de rezervare cap-coadă (TO-40), pe datele de test din V2. Fiecare test rulează
 * într-o tranzacție anulată la final.
 *
 * <ul>
 *   <li>utilizatorul 6 (Andrei Popescu, fără 2FA) este membru al grantului 1 (120 de ore, 114
 *       rămase);
 *   <li>utilizatorul 8 (Ștefan Rusu, fără 2FA) este membru doar al grantului 2 (80 de ore, 78,5
 *       rămase);
 *   <li>resursa 1 (Braț robotic UR5, 15 minute de pregătire) are rezervarea 2 (Aprobata), mutată de
 *       test poimâine, 10:00–12:00; resursa 3 (Sala C204) nu are pregătire; resursa 6 este în
 *       mentenanță.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationCreateIntegrationTest {

  private static final String STUDENT_GRANT_1 = "andrei.popescu@student.usv.ro";
  private static final String STUDENT_GRANT_2 = "stefan.rusu@student.usv.ro";
  private static final ZoneOffset ROMANIA = ZoneOffset.ofHours(2);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  /** Intervalul rezervării 2, cu fusul orar al României, cum îl trimite frontend-ul. */
  private OffsetDateTime approvedStart;

  private OffsetDateTime approvedEnd;

  @BeforeEach
  void moveApprovedReservationToFuture() {
    // Datele din V2 sunt relative la ziua migrării; într-o bază mai veche, „mâine” a trecut deja.
    jdbc.update(
        "UPDATE rezervare SET start = date_trunc('day', now()) + interval '2 days 10 hours',"
            + " sfarsit = date_trunc('day', now()) + interval '2 days 12 hours' WHERE id = 2");
    approvedStart = timeOf("start", 2);
    approvedEnd = timeOf("sfarsit", 2);
  }

  @Test
  void create_freeSlot_returns201AndReservesGrantHours() throws Exception {
    String token = login(STUDENT_GRANT_1);
    final BigDecimal hoursBefore = remainingHours(1);
    OffsetDateTime start = approvedStart.plusDays(2);

    String body =
        create(token, 3, 1, start, start.plusHours(2))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, Matchers.containsString("/api/v1/")))
            .andExpect(jsonPath("$.stare").value("InAsteptareCoordonator"))
            .andExpect(jsonPath("$.solicitantId").value(6))
            .andExpect(jsonPath("$.resursaId").value(3))
            .andExpect(jsonPath("$.grantId").value(1))
            .andExpect(jsonPath("$.oreSolicitate").value(2.0))
            .andExpect(jsonPath("$.dataCreare").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    entityManager.flush();
    Integer id = JsonPath.read(body, "$.id");
    assertThat(
            jdbc.queryForObject(
                "SELECT stare FROM rezervare WHERE id = ?", String.class, id.longValue()))
        .isEqualTo("InAsteptareCoordonator");
    assertThat(remainingHours(1)).isEqualByComparingTo(hoursBefore.subtract(BigDecimal.TWO));
  }

  @Test
  void create_insidePreparationTimeOfOtherReservation_returns409WithFreeIntervals()
      throws Exception {
    String token = login(STUDENT_GRANT_1);
    // Rezervarea 2 blochează resursa 1 încă 15 minute după sfârșit (timpul de pregătire).
    OffsetDateTime start = approvedEnd.plusMinutes(10);

    create(token, 1, 1, start, start.plusHours(1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTERVAL_OCUPAT"))
        .andExpect(
            jsonPath(
                "$.intervaleLibere[*].start",
                Matchers.hasItem(approvedEnd.plusMinutes(15).toInstant().toString())))
        .andExpect(
            jsonPath(
                "$.intervaleLibere[*].sfarsit",
                Matchers.hasItem(approvedStart.minusMinutes(15).toInstant().toString())));
  }

  @Test
  void create_slotOfCancelledReservations_returns201() throws Exception {
    // Rezervarea 7 (Anulata) are deja intervalul rezervării 2; după anularea lui 2, intervalul
    // e liber, chiar dacă rămân două rezervări anulate pe el.
    entityManager.flush();
    jdbc.update("UPDATE rezervare SET stare = 'Anulata' WHERE id = 2");
    entityManager.clear();
    String token = login(STUDENT_GRANT_1);

    create(token, 1, 1, approvedStart, approvedEnd)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stare").value("InAsteptareCoordonator"));
  }

  @Test
  void create_resourceInMaintenance_returns422() throws Exception {
    String token = login(STUDENT_GRANT_1);
    OffsetDateTime start = approvedStart.plusDays(2);

    create(token, 6, 1, start, start.plusHours(1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("RESURSA_INDISPONIBILA"));
  }

  @Test
  void create_moreHoursThanGrantBalance_returns422() throws Exception {
    String token = login(STUDENT_GRANT_2);
    OffsetDateTime start = approvedStart.plusDays(2);

    create(token, 3, 2, start, start.plusHours(79))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SOLD_INSUFICIENT"))
        .andExpect(jsonPath("$.message", Matchers.containsString("78,5")));
  }

  @Test
  void create_onGrantUserIsNotPartOf_returns403() throws Exception {
    String token = login(STUDENT_GRANT_2);
    OffsetDateTime start = approvedStart.plusDays(2);

    create(token, 3, 1, start, start.plusHours(1))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));
  }

  @Test
  void create_unknownGrant_returns404() throws Exception {
    String token = login(STUDENT_GRANT_1);
    OffsetDateTime start = approvedStart.plusDays(2);

    create(token, 3, 999_999, start, start.plusHours(1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("GRANT_INEXISTENT"));
  }

  @Test
  void create_unknownResource_returns404() throws Exception {
    String token = login(STUDENT_GRANT_1);
    OffsetDateTime start = approvedStart.plusDays(2);

    create(token, 999_999, 1, start, start.plusHours(1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESURSA_INEXISTENTA"));
  }

  @Test
  void create_startInPast_returns400() throws Exception {
    String token = login(STUDENT_GRANT_1);
    OffsetDateTime start = OffsetDateTime.now(ROMANIA).minus(Duration.ofHours(1));

    create(token, 3, 1, start, start.plusHours(2))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INTERVAL_INVALID"));
  }

  private ResultActions create(
      String token, long resourceId, long grantId, OffsetDateTime start, OffsetDateTime end)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/reservations")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"resursaId": %d, "grantId": %d, "start": "%s", "sfarsit": "%s",
                 "scop": "Test integrare TO-40"}
                """
                    .formatted(resourceId, grantId, start, end)));
  }

  private OffsetDateTime timeOf(String column, long reservationId) {
    return jdbc.queryForObject(
            "SELECT " + column + " FROM rezervare WHERE id = ?",
            OffsetDateTime.class,
            reservationId)
        .withOffsetSameInstant(ROMANIA);
  }

  private BigDecimal remainingHours(long grantId) {
    return jdbc.queryForObject(
        "SELECT ore_ramase FROM v_sold_grant WHERE id = ?", BigDecimal.class, grantId);
  }

  private String login(String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "parola": "parola123"}
                        """
                            .formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.tokens.accessToken");
  }
}
