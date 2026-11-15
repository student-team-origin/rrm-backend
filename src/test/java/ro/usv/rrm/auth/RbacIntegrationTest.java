package ro.usv.rrm.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC cu scop cap-coadă (TO-33), cu login real pe utilizatorii din datele de test.
 *
 * <p>Utilizatorii folosiți:
 *
 * <ul>
 *   <li>1 admin@usv.ro: Administrator Sistem (global);
 *   <li>3 ana.rusu@usv.ro: Responsabil Laborator pentru laboratorul 2;
 *   <li>5 elena.marin@usv.ro: Coordonator Grant pentru grantul 2;
 *   <li>6 andrei.popescu@student.usv.ro: Student, membru al grantului 1.
 * </ul>
 *
 * <p>2FA (TO-32) nu e implementat încă, deci îl dezactivăm în tranzacția testului pentru
 * utilizatorii care îl au. Fiecare test se anulează la final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RbacIntegrationTest {

  private static final String ADMIN = "admin@usv.ro";
  private static final String LAB_MANAGER_LAB_2 = "ana.rusu@usv.ro";
  private static final String COORDINATOR_GRANT_2 = "elena.marin@usv.ro";
  private static final String STUDENT = "andrei.popescu@student.usv.ro";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  // ───────────── Criteriul de acceptare: endpoint de administrare ─────────────

  @Test
  void student_callingAdminEndpoint_gets403() throws Exception {
    mockMvc
        .perform(as(STUDENT, get("/api/v1/users")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));
  }

  @Test
  void admin_callingAdminEndpoint_getsAllUsers() throws Exception {
    mockMvc
        .perform(as(ADMIN, get("/api/v1/users")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(8));
    mockMvc
        .perform(as(ADMIN, get("/api/v1/users").param("rol", "Student")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3));
  }

  @Test
  void user_canReadOwnProfileButNotOthers() throws Exception {
    mockMvc.perform(as(STUDENT, get("/api/v1/users/6"))).andExpect(status().isOk());
    mockMvc.perform(as(STUDENT, get("/api/v1/users/7"))).andExpect(status().isForbidden());
  }

  // ───────────── Granturi: fiecare vede doar granturile sale ─────────────

  @Test
  void coordinator_seesOnlyOwnGrants() throws Exception {
    mockMvc
        .perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].cod").value("ERASMUS-DIGILAB"));
    mockMvc
        .perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants/2")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.oreAlocate").value(80))
        .andExpect(jsonPath("$.oreRamase").isNumber());
    mockMvc
        .perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants/1")))
        .andExpect(status().isForbidden());
  }

  @Test
  void grantVisibility_followsCoordinatorRole_notGrantColumn() throws Exception {
    // Coloana coordonator_id indică alt utilizator, dar rolul CoordonatorGrant pe grantul 2 rămâne:
    // Elena vede grantul în continuare, la fel cum îi poate aproba rezervările.
    jdbc.update("UPDATE grant_cercetare SET coordonator_id = 4 WHERE id = 2");
    mockMvc.perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants/2"))).andExpect(status().isOk());

    // Fără rol (și fără să fie membru), nu mai vede grantul.
    jdbc.update("DELETE FROM utilizator_rol WHERE utilizator_id = 5 AND rol = 'CoordonatorGrant'");
    // Totul rulează într-o tranzacție: Hibernate ar refolosi rolurile încărcate la login-ul
    // anterior.
    entityManager.clear();
    mockMvc
        .perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(as(COORDINATOR_GRANT_2, get("/api/v1/grants/2")))
        .andExpect(status().isForbidden());
  }

  @Test
  void member_seesGrantsTheyBelongTo() throws Exception {
    mockMvc
        .perform(as(STUDENT, get("/api/v1/grants")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].cod").value("PN-III-P4-2026"));
  }

  @Test
  void admin_seesAllGrants() throws Exception {
    mockMvc
        .perform(as(ADMIN, get("/api/v1/grants")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void unknownGrant_returns404NotForbidden() throws Exception {
    mockMvc
        .perform(as(STUDENT, get("/api/v1/grants/999")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("GRANT_INEXISTENT"));
  }

  // ───────────── Resurse: responsabilul gestionează doar laboratorul său ─────────────

  @Test
  void student_cannotCreateResource() throws Exception {
    mockMvc
        .perform(
            as(STUDENT, post("/api/v1/resources"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(newResource(2)))
        .andExpect(status().isForbidden());
  }

  @Test
  void labManager_managesResourcesOnlyInOwnLaboratory() throws Exception {
    mockMvc
        .perform(
            as(LAB_MANAGER_LAB_2, post("/api/v1/resources"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(newResource(2)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            as(LAB_MANAGER_LAB_2, post("/api/v1/resources"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(newResource(1)))
        .andExpect(status().isForbidden());

    // Resursa 4 este în laboratorul 2, resursa 1 în laboratorul 1.
    mockMvc
        .perform(
            as(LAB_MANAGER_LAB_2, patch("/api/v1/resources/4/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stare\": \"Mentenanta\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            as(LAB_MANAGER_LAB_2, patch("/api/v1/resources/1/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stare\": \"Mentenanta\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void labManager_unknownResource_gets404NotForbidden() throws Exception {
    mockMvc
        .perform(
            as(LAB_MANAGER_LAB_2, patch("/api/v1/resources/999/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stare\": \"Mentenanta\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void anyAuthenticatedUser_canReadCatalog() throws Exception {
    mockMvc.perform(as(STUDENT, get("/api/v1/resources"))).andExpect(status().isOk());
    mockMvc.perform(as(STUDENT, get("/api/v1/labs"))).andExpect(status().isOk());
  }

  /** Cererea dată, cu tokenul obținut prin login pentru utilizatorul dat. */
  private MockHttpServletRequestBuilder as(String email, MockHttpServletRequestBuilder request)
      throws Exception {
    jdbc.update("UPDATE utilizator SET doi_factori_activ = FALSE WHERE email = ?", email);
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"%s\", \"parola\": \"parola123\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(body, "$.tokens.accessToken");
    return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private static String newResource(long laboratoryId) {
    return "{\"denumire\": \"Generator de semnal\", \"categorieId\": 2, \"laboratorId\": %d}"
        .formatted(laboratoryId);
  }
}
