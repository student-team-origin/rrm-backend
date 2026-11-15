package ro.usv.rrm.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import ro.usv.rrm.approval.ApprovalDecision;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.SessionService;
import ro.usv.rrm.common.config.RrmProperties;
import ro.usv.rrm.common.config.SecurityConfig;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;
import ro.usv.rrm.resource.ResourceStatus;
import ro.usv.rrm.resource.dto.ResourceCategoryResponse;
import ro.usv.rrm.resource.dto.ResourceResponse;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.dto.UserResponse;
import ro.usv.rrm.user.dto.UserRoleResponse;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(RrmProperties.class)
class ReservationControllerTest {

  private static final AuthenticatedUser USER =
      new AuthenticatedUser(6, UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10"), List.of());

  private static final Instant START = Instant.parse("2026-11-16T12:00:00Z");

  private static final ReservationResponse RESERVATION =
      new ReservationResponse(
          3L,
          5L,
          1L,
          6L,
          ReservationStatus.PENDING_LABORATORY,
          START,
          START.plusSeconds(7200),
          new BigDecimal("2.00"),
          "Imprimarea carcasei pentru senzorul de proximitate",
          Instant.parse("2026-11-15T07:00:00Z"),
          null);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ReservationService reservationService;

  /** Cerut de filtrul JWT din SecurityConfig; testele pun direct utilizatorul autentificat. */
  @MockitoBean private SessionService sessionService;

  // ───────────── GET /reservations ─────────────

  @Test
  void getReservations_withFilters_passesThemForCurrentUser() throws Exception {
    ReservationFilter filter =
        new ReservationFilter(
            ReservationStatus.PENDING_LABORATORY,
            Instant.parse("2026-11-15T22:00:00Z"),
            Instant.parse("2026-11-22T22:00:00Z"));
    given(reservationService.findMine(6L, filter, 1, 10))
        .willReturn(new PageResponse<>(List.of(RESERVATION), 1, 10, 11));

    mockMvc
        .perform(
            get("/api/v1/reservations")
                .with(authentication(authenticated(USER)))
                .param("stare", "InAsteptareLaborator")
                .param("from", "2026-11-16T00:00:00+02:00")
                .param("to", "2026-11-23T00:00:00+02:00")
                .param("page", "1")
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.total").value(11))
        .andExpect(jsonPath("$.items[0].id").value(3))
        .andExpect(jsonPath("$.items[0].resursaId").value(5))
        .andExpect(jsonPath("$.items[0].grantId").value(1))
        .andExpect(jsonPath("$.items[0].solicitantId").value(6))
        .andExpect(jsonPath("$.items[0].stare").value("InAsteptareLaborator"))
        .andExpect(jsonPath("$.items[0].start").value("2026-11-16T12:00:00Z"))
        .andExpect(jsonPath("$.items[0].sfarsit").value("2026-11-16T14:00:00Z"))
        .andExpect(jsonPath("$.items[0].oreSolicitate").isNumber())
        .andExpect(jsonPath("$.items[0].dataCheckIn").isEmpty());
  }

  @Test
  void getReservations_withoutFilters_usesDefaultPage() throws Exception {
    given(reservationService.findMine(6L, new ReservationFilter(null, null, null), 0, 20))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0));

    mockMvc
        .perform(get("/api/v1/reservations").with(authentication(authenticated(USER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void getReservations_unknownStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reservations")
                .with(authentication(authenticated(USER)))
                .param("stare", "Aprobat"))
        .andExpect(status().isBadRequest());

    then(reservationService).should(never()).findMine(anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void getReservations_fromWithoutOffset_returns400WithExpectedFormat() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reservations")
                .with(authentication(authenticated(USER)))
                .param("from", "2026-11-16T00:00:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CERERE_INVALIDA"))
        .andExpect(jsonPath("$.fields.from").exists());

    then(reservationService).should(never()).findMine(anyLong(), any(), anyInt(), anyInt());
  }

  @Test
  void getReservations_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/reservations")).andExpect(status().isUnauthorized());
  }

  // ───────────── GET /reservations/{id} ─────────────

  @Test
  void getReservation_allowed_returnsDetailWithApprovals() throws Exception {
    given(reservationService.findById(3L, USER)).willReturn(detail());

    mockMvc
        .perform(get("/api/v1/reservations/3").with(authentication(authenticated(USER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.stare").value("InAsteptareLaborator"))
        .andExpect(jsonPath("$.resursa.denumire").value("Imprimantă 3D Prusa MK4"))
        .andExpect(jsonPath("$.resursa.categorie.denumire").value("Echipament"))
        .andExpect(jsonPath("$.solicitant.nume").value("Andrei Popescu"))
        .andExpect(jsonPath("$.solicitant.roluri[0].rol").value("Student"))
        .andExpect(jsonPath("$.aprobari[0].nivel").value(1))
        .andExpect(jsonPath("$.aprobari[0].aprobatorId").value(4))
        .andExpect(jsonPath("$.aprobari[0].decizie").value("Aprobat"));
  }

  @Test
  void getReservation_notAllowed_returns403() throws Exception {
    given(reservationService.findById(2L, USER))
        .willThrow(ApiException.forbidden("REZERVARE_INACCESIBILA", "Nu ai acces."));

    mockMvc
        .perform(get("/api/v1/reservations/2").with(authentication(authenticated(USER))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REZERVARE_INACCESIBILA"));
  }

  @Test
  void getReservation_unknownId_returns404() throws Exception {
    given(reservationService.findById(999L, USER))
        .willThrow(ApiException.notFound("REZERVARE_INEXISTENTA", "Nu există."));

    mockMvc
        .perform(get("/api/v1/reservations/999").with(authentication(authenticated(USER))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REZERVARE_INEXISTENTA"));
  }

  // ───────────── POST /reservations/{id}/cancel ─────────────

  @Test
  void cancelReservation_withReason_returnsCancelledReservation() throws Exception {
    given(reservationService.cancel(3L, 6L, "Nu mai am nevoie"))
        .willReturn(withStatus(ReservationStatus.CANCELLED));

    mockMvc
        .perform(
            post("/api/v1/reservations/3/cancel")
                .with(authentication(authenticated(USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motiv": "Nu mai am nevoie"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.stare").value("Anulata"));
  }

  @Test
  void cancelReservation_withoutBody_cancelsWithoutReason() throws Exception {
    given(reservationService.cancel(3L, 6L, null))
        .willReturn(withStatus(ReservationStatus.CANCELLED));

    mockMvc
        .perform(post("/api/v1/reservations/3/cancel").with(authentication(authenticated(USER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stare").value("Anulata"));
  }

  @Test
  void cancelReservation_reasonTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/reservations/3/cancel")
                .with(authentication(authenticated(USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motiv\": \"" + "x".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.motiv").exists());

    then(reservationService).should(never()).cancel(anyLong(), anyLong(), any());
  }

  @Test
  void cancelReservation_afterStart_returns409() throws Exception {
    given(reservationService.cancel(3L, 6L, null))
        .willThrow(ApiException.conflict("REZERVARE_INCEPUTA", "A început deja."));

    mockMvc
        .perform(post("/api/v1/reservations/3/cancel").with(authentication(authenticated(USER))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REZERVARE_INCEPUTA"));
  }

  @Test
  void cancelReservation_otherUsersReservation_returns403() throws Exception {
    given(reservationService.cancel(2L, 6L, null))
        .willThrow(ApiException.forbidden("REZERVARE_ALTUI_UTILIZATOR", "Doar ale tale."));

    mockMvc
        .perform(post("/api/v1/reservations/2/cancel").with(authentication(authenticated(USER))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("REZERVARE_ALTUI_UTILIZATOR"));
  }

  @Test
  void cancelReservation_unknownId_returns404() throws Exception {
    given(reservationService.cancel(999L, 6L, null))
        .willThrow(ApiException.notFound("REZERVARE_INEXISTENTA", "Nu există."));

    mockMvc
        .perform(post("/api/v1/reservations/999/cancel").with(authentication(authenticated(USER))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REZERVARE_INEXISTENTA"));
  }

  // ───────────── date de test ─────────────

  private static ReservationResponse withStatus(ReservationStatus status) {
    return new ReservationResponse(
        RESERVATION.id(),
        RESERVATION.resursaId(),
        RESERVATION.grantId(),
        RESERVATION.solicitantId(),
        status,
        RESERVATION.start(),
        RESERVATION.sfarsit(),
        RESERVATION.oreSolicitate(),
        RESERVATION.scop(),
        RESERVATION.dataCreare(),
        RESERVATION.dataCheckIn());
  }

  private static ReservationDetailResponse detail() {
    return new ReservationDetailResponse(
        RESERVATION.id(),
        RESERVATION.resursaId(),
        RESERVATION.grantId(),
        RESERVATION.solicitantId(),
        RESERVATION.stare(),
        RESERVATION.start(),
        RESERVATION.sfarsit(),
        RESERVATION.oreSolicitate(),
        RESERVATION.scop(),
        RESERVATION.dataCreare(),
        RESERVATION.dataCheckIn(),
        new ResourceResponse(
            5L,
            "Imprimantă 3D Prusa MK4",
            "Volum de lucru 250 × 210 × 220 mm",
            ResourceStatus.ACTIVE,
            30,
            null,
            new ResourceCategoryResponse(2L, "Echipament"),
            2L),
        new UserResponse(
            6L,
            "Andrei Popescu",
            "andrei.popescu@student.usv.ro",
            true,
            false,
            List.of(new UserRoleResponse(Role.STUDENT, ScopeType.GLOBAL, null))),
        List.of(
            new ApprovalResponse(
                10L,
                3L,
                4L,
                1,
                ApprovalDecision.APPROVED,
                "Încadrat în bugetul de ore.",
                Instant.parse("2026-11-15T07:30:00Z"))));
  }

  private static UsernamePasswordAuthenticationToken authenticated(AuthenticatedUser user) {
    return new UsernamePasswordAuthenticationToken(user, null, List.of());
  }
}
