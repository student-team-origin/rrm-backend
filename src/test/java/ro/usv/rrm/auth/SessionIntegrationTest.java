package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
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
 * Sesiunea cap-coadă (TO-31): token valid, logout și expirarea din inactivitate, pe utilizatorul 6
 * din datele de test (Andrei Popescu, student, fără 2FA). Fiecare test rulează într-o tranzacție
 * anulată la final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionIntegrationTest {

  private static final String LOGIN_JSON =
      """
      {"email": "andrei.popescu@student.usv.ro", "parola": "parola123"}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void me_withValidToken_returnsUserAndExtendsSession() throws Exception {
    String token = login();
    updateSessions("UPDATE sesiune SET expira_la = now() + interval '5 minutes'");

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(6))
        .andExpect(jsonPath("$.email").value("andrei.popescu@student.usv.ro"))
        .andExpect(jsonPath("$.roluri[0].rol").value("Student"))
        .andExpect(jsonPath("$.roluri[0].tipScop").value("Global"));

    entityManager.flush();
    OffsetDateTime expiresAt =
        jdbc.queryForObject(
            "SELECT expira_la FROM sesiune WHERE utilizator_id = 6", OffsetDateTime.class);
    assertThat(expiresAt).isAfter(OffsetDateTime.now().plusMinutes(59));
  }

  @Test
  void anyRequest_afterLogout_returns401() throws Exception {
    String token = login();

    mockMvc
        .perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESIUNE_EXPIRATA"));
    mockMvc
        .perform(get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized());
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT revocata FROM sesiune WHERE utilizator_id = 6", Boolean.class))
        .isTrue();
  }

  @Test
  void anyRequest_after60MinutesOfInactivity_returns401() throws Exception {
    String token = login();
    // Ultima activitate acum 61 de minute: sesiunea a expirat la minutul 60.
    updateSessions(
        "UPDATE sesiune SET ultima_activitate = now() - interval '61 minutes',"
            + " expira_la = now() - interval '1 minute'");

    mockMvc
        .perform(get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESIUNE_EXPIRATA"));
  }

  @Test
  void protectedEndpoint_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/resources"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("NEAUTENTIFICAT"));
  }

  @Test
  void protectedEndpoint_withInvalidToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, bearer("abc.def.ghi")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
  }

  @Test
  void protectedEndpoint_withValidToken_returns200() throws Exception {
    String token = login();

    mockMvc
        .perform(get("/api/v1/resources").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk());
  }

  private String login() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.tokens.accessToken");
  }

  /**
   * Modifică sesiunile direct în baza de date (ex. simulează inactivitatea) și golește cache-ul
   * Hibernate, ca următoarea cerere din aceeași tranzacție să citească valorile noi.
   */
  private void updateSessions(String sql) {
    entityManager.flush();
    jdbc.update(sql);
    entityManager.clear();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
