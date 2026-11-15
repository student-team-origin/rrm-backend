package ro.usv.rrm.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.ScopedRole;
import ro.usv.rrm.auth.SessionService;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.reservation.ReservationStatus;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.resource.ResourceStatus;
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;
import ro.usv.rrm.resource.dto.ResourceResponse;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.dto.UserResponse;
import ro.usv.rrm.user.dto.UserRoleResponse;

@WebMvcTest(ApprovalController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class ApprovalControllerTest {

  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");

  private static final AuthenticatedUser COORDINATOR =
      new AuthenticatedUser(
          5, SESSION_ID, List.of(new ScopedRole(Role.GRANT_COORDINATOR, ScopeType.GRANT, 2L)));

  private static final AuthenticatedUser STUDENT =
      new AuthenticatedUser(
          6, SESSION_ID, List.of(new ScopedRole(Role.STUDENT, ScopeType.GLOBAL, null)));

  private static final Instant DECIDED_AT = Instant.parse("2026-11-15T19:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApprovalFlowService approvalFlowService;

  /** Cerut de filtrul JWT din SecurityConfig; testele pun direct utilizatorul autentificat. */
  @MockitoBean private SessionService sessionService;

  // ───────────── GET /approvals ─────────────

  @Test
  void getApprovals_withoutStatus_returnsPendingRequests() throws Exception {
    given(approvalFlowService.findForApprover(COORDINATOR, ApprovalListStatus.PENDING))
        .willReturn(List.of(pendingReservation()));

    mockMvc
        .perform(get("/api/v1/approvals").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(4))
        .andExpect(jsonPath("$[0].stare").value("InAsteptareCoordonator"))
        .andExpect(jsonPath("$[0].resursa.denumire").value("Osciloscop Rigol DS1054"))
        .andExpect(jsonPath("$[0].solicitant.nume").value("Ștefan Rusu"))
        .andExpect(jsonPath("$[0].aprobari").isEmpty());
  }

  @Test
  void getApprovals_rejected_passesFilter() throws Exception {
    given(approvalFlowService.findForApprover(COORDINATOR, ApprovalListStatus.REJECTED))
        .willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/approvals")
                .with(authentication(authenticated(COORDINATOR)))
                .param("stare", "respinse"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getApprovals_unknownStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/approvals")
                .with(authentication(authenticated(COORDINATOR)))
                .param("stare", "Aprobata"))
        .andExpect(status().isBadRequest());

    then(approvalFlowService).should(never()).findForApprover(any(), any());
  }

  @Test
  void getApprovals_student_returns403WithoutCallingService() throws Exception {
    mockMvc
        .perform(get("/api/v1/approvals").with(authentication(authenticated(STUDENT))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));

    then(approvalFlowService).should(never()).findForApprover(any(), any());
  }

  @Test
  void getApprovals_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/approvals")).andExpect(status().isUnauthorized());
  }

  // ───────────── POST /approvals/{id}/approve ─────────────

  @Test
  void approve_withObservation_returnsDecision() throws Exception {
    given(approvalFlowService.approve(4L, COORDINATOR, "Încadrat în buget"))
        .willReturn(decision(ApprovalDecision.APPROVED, "Încadrat în buget"));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .with(authentication(authenticated(COORDINATOR)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "Încadrat în buget"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rezervareId").value(4))
        .andExpect(jsonPath("$.aprobatorId").value(5))
        .andExpect(jsonPath("$.nivel").value(1))
        .andExpect(jsonPath("$.decizie").value("Aprobat"))
        .andExpect(jsonPath("$.observatie").value("Încadrat în buget"))
        .andExpect(jsonPath("$.dataDecizie").value("2026-11-15T19:00:00Z"));
  }

  @Test
  void approve_withoutBody_approvesWithoutObservation() throws Exception {
    given(approvalFlowService.approve(4L, COORDINATOR, null))
        .willReturn(decision(ApprovalDecision.APPROVED, null));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decizie").value("Aprobat"));
  }

  @Test
  void approve_observationTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve")
                .with(authentication(authenticated(COORDINATOR)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"observatie\": \"" + "x".repeat(1001) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.observatie").exists());

    then(approvalFlowService).should(never()).approve(anyLong(), any(), any());
  }

  @Test
  void approve_student_returns403WithoutCallingService() throws Exception {
    mockMvc
        .perform(post("/api/v1/approvals/4/approve").with(authentication(authenticated(STUDENT))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));

    then(approvalFlowService).should(never()).approve(anyLong(), any(), any());
  }

  @Test
  void reject_student_returns403WithoutCallingService() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject")
                .with(authentication(authenticated(STUDENT)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "Motiv"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCES_INTERZIS"));

    then(approvalFlowService).should(never()).reject(anyLong(), any(), any());
  }

  @Test
  void approve_wrongApprover_returns403() throws Exception {
    given(approvalFlowService.approve(4L, COORDINATOR, null))
        .willThrow(ApiException.forbidden("FARA_DREPT_APROBARE", "Nu poți decide."));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FARA_DREPT_APROBARE"));
  }

  @Test
  void approve_notAwaitingDecision_returns409() throws Exception {
    given(approvalFlowService.approve(4L, COORDINATOR, null))
        .willThrow(ApiException.conflict("CERERE_FARA_DECIZIE_IN_ASTEPTARE", "Nu mai așteaptă."));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/approve").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CERERE_FARA_DECIZIE_IN_ASTEPTARE"));
  }

  @Test
  void approve_unknownReservation_returns404() throws Exception {
    given(approvalFlowService.approve(999L, COORDINATOR, null))
        .willThrow(ApiException.notFound("REZERVARE_INEXISTENTA", "Nu există."));

    mockMvc
        .perform(
            post("/api/v1/approvals/999/approve").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REZERVARE_INEXISTENTA"));
  }

  // ───────────── POST /approvals/{id}/reject ─────────────

  @Test
  void reject_withReason_returnsDecision() throws Exception {
    given(approvalFlowService.reject(4L, COORDINATOR, "Nu ține de grant"))
        .willReturn(decision(ApprovalDecision.REJECTED, "Nu ține de grant"));

    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject")
                .with(authentication(authenticated(COORDINATOR)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "Nu ține de grant"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decizie").value("Respins"))
        .andExpect(jsonPath("$.observatie").value("Nu ține de grant"));
  }

  @Test
  void reject_blankReason_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject")
                .with(authentication(authenticated(COORDINATOR)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observatie": "  "}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.observatie").value("Motivul respingerii este obligatoriu."));

    then(approvalFlowService).should(never()).reject(anyLong(), any(), any());
  }

  @Test
  void reject_withoutBody_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approvals/4/reject").with(authentication(authenticated(COORDINATOR))))
        .andExpect(status().isBadRequest());

    then(approvalFlowService).should(never()).reject(anyLong(), any(), any());
  }

  // ───────────── date de test ─────────────

  private static ApprovalResponse decision(ApprovalDecision decision, String observation) {
    return new ApprovalResponse(50L, 4L, 5L, 1, decision, observation, DECIDED_AT);
  }

  private static ReservationDetailResponse pendingReservation() {
    Instant start = Instant.parse("2026-11-17T07:00:00Z");
    return new ReservationDetailResponse(
        4L,
        4L,
        2L,
        8L,
        ReservationStatus.PENDING_COORDINATOR,
        start,
        start.plusSeconds(5400),
        new BigDecimal("1.50"),
        "Măsurători pe placa de achiziție DigiLab",
        Instant.parse("2026-11-15T18:30:00Z"),
        null,
        new ResourceResponse(
            4L,
            "Osciloscop Rigol DS1054",
            "4 canale, 50 MHz",
            ResourceStatus.ACTIVE,
            5,
            null,
            new ResourceCategoryResponse(2L, "Echipament"),
            2L),
        new UserResponse(
            8L,
            "Ștefan Rusu",
            "stefan.rusu@student.usv.ro",
            true,
            false,
            List.of(new UserRoleResponse(Role.STUDENT, ScopeType.GLOBAL, null))),
        List.of());
  }

  private static UsernamePasswordAuthenticationToken authenticated(AuthenticatedUser user) {
    return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
  }
}
