package ro.usv.rrm.approval;

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
 * Fluxul de aprobare cap-coadă (TO-43), pe datele de test din V2. Fiecare test rulează într-o
 * tranzacție anulată la final.
 *
 * <ul>
 *   <li>rezervarea 4 (InAsteptareCoordonator): utilizatorul 8, Osciloscop (laboratorul 2), grantul
 *       2;
 *   <li>utilizatorul 5 (Elena Marin, fără 2FA): Coordonator pentru grantul 2;
 *   <li>utilizatorul 3 (Ana Rusu): Responsabil pentru laboratorul 2. Are 2FA, deci testele i-l
 *       dezactivează ca să primească tokenul direct la login.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalIntegrationTest {

  private static final String STUDENT = "andrei.popescu@student.usv.ro";
  private static final String COORDINATOR_GRANT_2 = "elena.marin@usv.ro";
  private static final String MANAGER_LAB_2 = "ana.rusu@usv.ro";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void approve_bothLevels_reservationBecomesApproved() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);
    mockMvc
        .perform(get("/api/v1/approvals").header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", Matchers.contains(4)));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "Încadrat în bugetul de ore."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nivel").value(1))
        .andExpect(jsonPath("$.decizie").value("Aprobat"))
        .andExpect(jsonPath("$.aprobatorId").value(5))
        .andExpect(jsonPath("$.dataDecizie").isNotEmpty());
    assertThat(statusOf(4)).isEqualTo("InAsteptareLaborator");

    String manager = loginWithout2fa(MANAGER_LAB_2, 3);
    mockMvc
        .perform(get("/api/v1/approvals").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", Matchers.containsInAnyOrder(3, 4)));
    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve").header(HttpHeaders.AUTHORIZATION, bearer(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nivel").value(2))
        .andExpect(jsonPath("$.aprobatorId").value(3));
    assertThat(statusOf(4)).isEqualTo("Aprobata");

    mockMvc
        .perform(
            get("/api/v1/reservations/4").header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stare").value("Aprobata"))
        .andExpect(jsonPath("$.aprobari[*].nivel", Matchers.contains(1, 2)));
    mockMvc
        .perform(
            get("/api/v1/approvals")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator))
                .param("stare", "aprobate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(4));
  }

  @Test
  void approve_coordinatorAtLaboratoryLevel_returns403() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);
    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FARA_DREPT_APROBARE"));
    assertThat(statusOf(4)).isEqualTo("InAsteptareLaborator");
  }

  @Test
  void approve_student_returns403() throws Exception {
    String student = login(STUDENT);

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve").header(HttpHeaders.AUTHORIZATION, bearer(student)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));
    mockMvc
        .perform(get("/api/v1/approvals").header(HttpHeaders.AUTHORIZATION, bearer(student)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));
    assertThat(statusOf(4)).isEqualTo("InAsteptareCoordonator");
  }

  @Test
  void approve_ownRequest_returns403() throws Exception {
    entityManager.flush();
    Long ownReservation =
        jdbc.queryForObject(
            "INSERT INTO rezervare (solicitant_id, resursa_id, grant_id, start, sfarsit,"
                + " ore_solicitate, scop)"
                + " VALUES (5, 4, 2, date_trunc('day', now()) + interval '5 days 9 hours',"
                + " date_trunc('day', now()) + interval '5 days 10 hours', 1.0, 'Cerere proprie')"
                + " RETURNING id",
            Long.class);
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(
            post("/api/v1/approvals/" + ownReservation + "/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTO_APROBARE_INTERZISA"));
    assertThat(statusOf(ownReservation)).isEqualTo("InAsteptareCoordonator");
  }

  @Test
  void approve_afterStart_returns409AndIsNotListed() throws Exception {
    entityManager.flush();
    jdbc.update("UPDATE rezervare SET start = now() - interval '3 hours' WHERE id = 4");
    entityManager.clear();
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(get("/api/v1/approvals").header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REZERVARE_INCEPUTA"));
    assertThat(statusOf(4)).isEqualTo("InAsteptareCoordonator");
  }

  @Test
  void approve_alreadyRejectedReservation_returns409() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(
            post("/api/v1/approvals/5/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CERERE_FARA_DECIZIE_IN_ASTEPTARE"));
  }

  @Test
  void reject_withReason_releasesIntervalAndHours() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);
    final BigDecimal hoursBefore = remainingHoursOfGrant2();

    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "Placa DigiLab nu ține de obiectivele grantului."}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decizie").value("Respins"))
        .andExpect(jsonPath("$.nivel").value(1));

    entityManager.flush();
    assertThat(statusOf(4)).isEqualTo("Respinsa");
    assertThat(remainingHoursOfGrant2())
        .isEqualByComparingTo(hoursBefore.add(new BigDecimal("1.5")));
    // Același interval pe aceeași resursă: ar încălca rezervare_fara_suprapunere dacă rezervarea 4
    // ar mai bloca resursa.
    int inserted =
        jdbc.update(
            "INSERT INTO rezervare (solicitant_id, resursa_id, grant_id, start, sfarsit,"
                + " ore_solicitate, scop)"
                + " SELECT 7, resursa_id, grant_id, start, sfarsit, ore_solicitate, 'Test'"
                + " FROM rezervare WHERE id = 4");
    assertThat(inserted).isEqualTo(1);
  }

  @Test
  void reject_withoutReason_returns400() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.observatie").exists());
    assertThat(statusOf(4)).isEqualTo("InAsteptareCoordonator");
  }

  @Test
  void getApprovals_rejectedByCoordinator_returnsHistory() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(
            get("/api/v1/approvals")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator))
                .param("stare", "respinse"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", Matchers.contains(5)))
        .andExpect(jsonPath("$[0].aprobari[0].decizie").value("Respins"));
  }

  @Test
  void approve_unknownReservation_returns404() throws Exception {
    String coordinator = login(COORDINATOR_GRANT_2);

    mockMvc
        .perform(
            post("/api/v1/approvals/999999/approve")
                .header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REZERVARE_INEXISTENTA"));
  }

  private String statusOf(long reservationId) {
    entityManager.flush();
    return jdbc.queryForObject(
        "SELECT stare FROM rezervare WHERE id = ?", String.class, reservationId);
  }

  private BigDecimal remainingHoursOfGrant2() {
    return jdbc.queryForObject(
        "SELECT ore_ramase FROM v_sold_grant WHERE id = 2", BigDecimal.class);
  }

  /** Dezactivează 2FA pentru utilizator (doar în tranzacția testului), apoi se autentifică. */
  private String loginWithout2fa(String email, long userId) throws Exception {
    entityManager.flush();
    jdbc.update("UPDATE utilizator SET doi_factori_activ = FALSE WHERE id = ?", userId);
    entityManager.clear();
    return login(email);
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
