package ro.usv.rrm.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Hard lock-ul (TO-40, criteriul de acceptare): două cereri simultane pe același interval al
 * aceleiași resurse, fiecare în tranzacția ei, din partea a doi utilizatori de pe granturi
 * diferite. Doar una reușește.
 *
 * <p>Fără {@code @Transactional}: cererile trebuie să se confirme fiecare în tranzacția proprie, ca
 * într-un apel real. Rândurile create de test (rezervări, sesiuni de login) se șterg la final.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationConcurrencyIntegrationTest {

  private static final long ROOM_C204 = 3L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private long lastReservationId;
  private long lastSessionId;

  @BeforeEach
  void rememberExistingRows() {
    lastReservationId = maxId("rezervare");
    lastSessionId = maxId("sesiune");
  }

  @AfterEach
  void deleteCreatedRows() {
    jdbc.update("DELETE FROM rezervare WHERE id > ?", lastReservationId);
    jdbc.update("DELETE FROM sesiune WHERE id > ?", lastSessionId);
  }

  @Test
  void create_twoSimultaneousRequestsForSameSlot_onlyOneSucceeds() throws Exception {
    // Utilizatorul 6 pe grantul 1 și utilizatorul 8 pe grantul 2: blocarea grantului nu le
    // serializează, deci doar blocarea resursei (și constrângerea din DB) le separă.
    List<String> tokens =
        List.of(login("andrei.popescu@student.usv.ro"), login("stefan.rusu@student.usv.ro"));
    long[] grants = {1, 2};

    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).truncatedTo(ChronoUnit.DAYS).plusHours(8);
    List<MvcResult> results = sendSimultaneously(tokens, grants, start);

    List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).toList();
    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    MvcResult rejected = results.get(statuses.indexOf(409));
    String rejectedBody = rejected.getResponse().getContentAsString();
    assertThat((String) JsonPath.read(rejectedBody, "$.code")).isEqualTo("INTERVAL_OCUPAT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rezervare WHERE resursa_id = ? AND id > ?"
                    + " AND stare = 'InAsteptareCoordonator'",
                Integer.class,
                ROOM_C204,
                lastReservationId))
        .isEqualTo(1);
  }

  /** Pornește cererile în același moment, pe fire separate (cererea i: tokens[i], grants[i]). */
  private List<MvcResult> sendSimultaneously(
      List<String> tokens, long[] grants, OffsetDateTime start) throws Exception {
    CountDownLatch ready = new CountDownLatch(tokens.size());
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
    try {
      List<Future<MvcResult>> futures = new ArrayList<>();
      for (int i = 0; i < tokens.size(); i++) {
        String token = tokens.get(i);
        long grantId = grants[i];
        futures.add(
            pool.submit(() -> {
              ready.countDown();
              go.await();
              return mockMvc
                  .perform(
                      post("/api/v1/reservations")
                          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              """
                              {"resursaId": %d, "grantId": %d, "start": "%s", "sfarsit": "%s",
                               "scop": "Test concurență TO-40"}
                              """
                                  .formatted(ROOM_C204, grantId, start, start.plusHours(2))))
                  .andReturn();
            }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<MvcResult> results = new ArrayList<>();
      for (Future<MvcResult> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private long maxId(String table) {
    return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
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
