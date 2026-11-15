package ro.usv.rrm.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
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
 * Rezervările cap-coadă (TO-72), pe datele de test din V2. Fiecare test rulează într-o tranzacție
 * anulată la final.
 *
 * <ul>
 *   <li>utilizatorul 6 (Andrei Popescu, student, fără 2FA) are rezervările 1 (Finalizata), 3
 *       (InAsteptareLaborator, mâine, Imprimantă 3D, grantul 1) și 7 (Anulata);
 *   <li>utilizatorul 7 (Maria Ionescu) are rezervarea 2 (Aprobata, mâine);
 *   <li>utilizatorul 5 (Elena Marin, fără 2FA) este Coordonator Grant pentru grantul 2, cu
 *       rezervările 4 și 5.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationIntegrationTest {

  private static final String STUDENT = "andrei.popescu@student.usv.ro";
  private static final String COORDINATOR_GRANT_2 = "elena.marin@usv.ro";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void getReservations_student_returnsOnlyOwnReservations() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(get("/api/v1/reservations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.items[*].solicitantId", Matchers.everyItem(Matchers.is(6))))
        .andExpect(jsonPath("$.items[*].id", Matchers.containsInAnyOrder(1, 3, 7)));
  }

  @Test
  void getReservations_byStatus_filtersOwnReservations() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(
            get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("stare", "InAsteptareLaborator"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(3));
  }

  @Test
  void getReservation_grantCoordinator_returnsDetailWithApprovals() throws Exception {
    String token = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(get("/api/v1/reservations/5").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stare").value("Respinsa"))
        .andExpect(jsonPath("$.solicitant.id").value(7))
        .andExpect(jsonPath("$.resursa.id").value(2))
        .andExpect(jsonPath("$.aprobari[0].nivel").value(1))
        .andExpect(jsonPath("$.aprobari[0].aprobatorId").value(5))
        .andExpect(jsonPath("$.aprobari[0].decizie").value("Respins"));
  }

  @Test
  void getReservation_otherStudentsReservation_returns403() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(get("/api/v1/reservations/2").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REZERVARE_INACCESIBILA"));
  }

  @Test
  void cancel_ownPendingReservation_releasesIntervalAndHours() throws Exception {
    String token = login(STUDENT);
    final BigDecimal hoursBefore = remainingHoursOfGrant1();

    mockMvc
        .perform(
            post("/api/v1/reservations/3/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motiv": "Am găsit altă imprimantă"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.stare").value("Anulata"));

    entityManager.flush();
    assertThat(jdbc.queryForObject("SELECT stare FROM rezervare WHERE id = 3", String.class))
        .isEqualTo("Anulata");
    assertThat(remainingHoursOfGrant1()).isEqualByComparingTo(hoursBefore.add(BigDecimal.TWO));
    // Același interval pe aceeași resursă: ar încălca rezervare_fara_suprapunere dacă rezervarea 3
    // ar mai bloca resursa.
    int inserted =
        jdbc.update(
            "INSERT INTO rezervare (solicitant_id, resursa_id, grant_id, start, sfarsit,"
                + " ore_solicitate, scop)"
                + " SELECT 7, resursa_id, grant_id, start, sfarsit, ore_solicitate, 'Test'"
                + " FROM rezervare WHERE id = 3");
    assertThat(inserted).isEqualTo(1);
  }

  @Test
  void cancel_afterStart_returns422() throws Exception {
    entityManager.flush();
    jdbc.update("UPDATE rezervare SET start = now() - interval '1 hour' WHERE id = 3");
    entityManager.clear();
    String token = login(STUDENT);

    mockMvc
        .perform(
            post("/api/v1/reservations/3/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("REZERVARE_INCEPUTA"));
  }

  @Test
  void cancel_completedReservation_returns422() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(
            post("/api/v1/reservations/1/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("ANULARE_NEPERMISA"));
  }

  @Test
  void cancel_otherUsersReservation_returns403AndKeepsIt() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(
            post("/api/v1/reservations/2/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REZERVARE_ALTUI_UTILIZATOR"));

    entityManager.flush();
    assertThat(jdbc.queryForObject("SELECT stare FROM rezervare WHERE id = 2", String.class))
        .isEqualTo("Aprobata");
  }

  @Test
  void cancel_unknownId_returns404() throws Exception {
    String token = login(STUDENT);

    mockMvc
        .perform(
            post("/api/v1/reservations/999999/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REZERVARE_INEXISTENTA"));
  }

  private BigDecimal remainingHoursOfGrant1() {
    return jdbc.queryForObject(
        "SELECT ore_ramase FROM v_sold_grant WHERE id = 1", BigDecimal.class);
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
