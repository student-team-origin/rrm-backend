package ro.usv.rrm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.auth.JwtService.AccessTokenClaims;

/**
 * {@code POST /api/v1/auth/login} cap-coadă, pe utilizatorii din datele de test (parola tuturor:
 * {@code parola123}). Fiecare test rulează într-o tranzacție anulată la final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

  /** Utilizatorul 6 din datele de test, fără 2FA. */
  private static final String STUDENT_EMAIL = "andrei.popescu@student.usv.ro";

  /** Utilizatorul 1 din datele de test, cu 2FA activ. */
  private static final String ADMIN_EMAIL = "admin@usv.ro";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void login_correctPassword_returnsTokenForStoredSession() throws Exception {
    String body =
        mockMvc
            .perform(login(STUDENT_EMAIL, "parola123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.twoFactorRequired").value(false))
            .andExpect(jsonPath("$.tokens.expiresIn").value(8 * 3600))
            .andReturn()
            .getResponse()
            .getContentAsString();

    AccessTokenClaims claims =
        jwtService.parseAccessToken(JsonPath.read(body, "$.tokens.accessToken"));
    assertThat(claims.userId()).isEqualTo(6);
    Integer activeSessions =
        jdbc.queryForObject(
            "SELECT count(*) FROM sesiune WHERE jti = ? AND utilizator_id = 6 AND NOT revocata"
                + " AND expira_la > now()",
            Integer.class,
            claims.sessionTokenId());
    assertThat(activeSessions).isEqualTo(1);
  }

  @Test
  void login_wrongPassword_returns401() throws Exception {
    mockMvc
        .perform(login(STUDENT_EMAIL, "gresita"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("CREDENTIALE_INVALIDE"));
  }

  @Test
  void login_unknownEmail_returns401() throws Exception {
    mockMvc
        .perform(login("nimeni@usv.ro", "parola123"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("CREDENTIALE_INVALIDE"));
  }

  @Test
  void login_twoFactorEnabled_returnsChallengeAndNoSession() throws Exception {
    Integer sessionsBefore = jdbc.queryForObject("SELECT count(*) FROM sesiune", Integer.class);

    String body =
        mockMvc
            .perform(login(ADMIN_EMAIL, "parola123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.twoFactorRequired").value(true))
            .andExpect(jsonPath("$.tokens").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(jwtService.parseTwoFactorChallenge(JsonPath.read(body, "$.challengeId")))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sesiune", Integer.class))
        .isEqualTo(sessionsBefore);
  }

  private static RequestBuilder login(String email, String password) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\": \"%s\", \"parola\": \"%s\"}".formatted(email, password));
  }
}
