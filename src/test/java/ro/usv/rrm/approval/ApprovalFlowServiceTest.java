package ro.usv.rrm.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.ScopedRole;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.laboratory.Laboratory;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationService;
import ro.usv.rrm.reservation.ReservationStatus;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.resource.ResourceCategory;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.User;
import ro.usv.rrm.user.UserService;

@ExtendWith(MockitoExtension.class)
class ApprovalFlowServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T19:00:00Z");
  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");
  private static final long GRANT_ID = 2L;
  private static final long LABORATORY_ID = 2L;
  private static final long REQUESTER_ID = 8L;

  private static final AuthenticatedUser COORDINATOR =
      user(5L, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));
  private static final AuthenticatedUser LAB_MANAGER =
      user(3L, role(Role.LAB_MANAGER, ScopeType.LABORATORY, LABORATORY_ID));
  private static final AuthenticatedUser STUDENT =
      user(6L, role(Role.STUDENT, ScopeType.GLOBAL, null));

  @Mock private ApprovalRepository approvalRepository;
  @Mock private ReservationService reservationService;
  @Mock private UserService userService;

  private ApprovalFlowService approvalFlowService;
  private Reservation reservation;

  @BeforeEach
  void setUp() {
    approvalFlowService =
        new ApprovalFlowService(
            approvalRepository, reservationService, userService, Clock.fixed(NOW, ZoneOffset.UTC));
    reservation = reservation(REQUESTER_ID, NOW.plus(Duration.ofDays(2)));
  }

  // ───────────── approve ─────────────

  @Test
  void approve_coordinatorAtLevel1_sendsRequestToLaboratory() {
    givenReservation();
    givenSaveReturnsApproval();

    ApprovalResponse response = approvalFlowService.approve(4L, COORDINATOR, "Încadrat în buget");

    assertThat(response.nivel()).isEqualTo(Approval.COORDINATOR_LEVEL);
    assertThat(response.decizie()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(response.aprobatorId()).isEqualTo(5L);
    assertThat(response.observatie()).isEqualTo("Încadrat în buget");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
  }

  @Test
  void approve_labManagerAtLevel2_approvesReservation() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservation();
    givenSaveReturnsApproval();

    ApprovalResponse response = approvalFlowService.approve(4L, LAB_MANAGER, null);

    assertThat(response.nivel()).isEqualTo(Approval.LABORATORY_LEVEL);
    assertThat(response.decizie()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void approve_labManagerAtLevel1_throwsForbidden() {
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, LAB_MANAGER, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_COORDINATOR);
    then(approvalRepository).should(never()).saveAndFlush(any());
  }

  @Test
  void approve_coordinatorAtLevel2_throwsForbidden() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
  }

  @Test
  void approve_coordinatorOfAnotherGrant_throwsForbidden() {
    givenReservation();
    AuthenticatedUser otherCoordinator =
        user(4L, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, 1L));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, otherCoordinator, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
  }

  @Test
  void approve_student_throwsForbidden() {
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, STUDENT, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
  }

  @Test
  void approve_ownRequestAsCoordinator_throwsForbidden() {
    reservation = reservation(5L, NOW.plus(Duration.ofDays(2)));
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "AUTO_APROBARE_INTERZISA");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_COORDINATOR);
  }

  @Test
  void approve_ownRequestAsLabManager_throwsForbidden() {
    reservation = reservation(3L, NOW.plus(Duration.ofDays(2)));
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, LAB_MANAGER, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "AUTO_APROBARE_INTERZISA");
  }

  @Test
  void approve_afterStart_throwsConflict() {
    reservation = reservation(REQUESTER_ID, NOW.minus(Duration.ofHours(3)));
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR, null));

    assertApiError(ex, HttpStatus.CONFLICT, "REZERVARE_INCEPUTA");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_COORDINATOR);
  }

  @Test
  void approve_exactlyAtStart_throwsConflict() {
    reservation = reservation(REQUESTER_ID, NOW);
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, LAB_MANAGER, null));

    assertApiError(ex, HttpStatus.CONFLICT, "REZERVARE_INCEPUTA");
  }

  @ParameterizedTest
  @EnumSource(
      names = {"PENDING_COORDINATOR", "PENDING_LABORATORY"},
      mode = EnumSource.Mode.EXCLUDE)
  void approve_notAwaitingDecision_throwsConflict(ReservationStatus status) {
    reservation.setStatus(status);
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR, null));

    assertApiError(ex, HttpStatus.CONFLICT, "CERERE_FARA_DECIZIE_IN_ASTEPTARE");
    assertThat(reservation.getStatus()).isEqualTo(status);
  }

  @Test
  void approve_unknownReservation_throwsNotFound() {
    given(reservationService.getForUpdate(999L))
        .willThrow(ApiException.notFound("REZERVARE_INEXISTENTA", "Nu există."));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(999L, COORDINATOR, null));

    assertApiError(ex, HttpStatus.NOT_FOUND, "REZERVARE_INEXISTENTA");
  }

  // ───────────── reject ─────────────

  @Test
  void reject_coordinatorAtLevel1_rejectsWithReason() {
    givenReservation();
    givenSaveReturnsApproval();

    ApprovalResponse response =
        approvalFlowService.reject(4L, COORDINATOR, "Nu ține de obiectivele grantului");

    assertThat(response.decizie()).isEqualTo(ApprovalDecision.REJECTED);
    assertThat(response.nivel()).isEqualTo(Approval.COORDINATOR_LEVEL);
    assertThat(response.observatie()).isEqualTo("Nu ține de obiectivele grantului");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
  }

  @Test
  void reject_labManagerAtLevel2_rejectsReservation() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservation();
    givenSaveReturnsApproval();

    ApprovalResponse response = approvalFlowService.reject(4L, LAB_MANAGER, "Resursa e ocupată");

    assertThat(response.nivel()).isEqualTo(Approval.LABORATORY_LEVEL);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
  }

  @Test
  void reject_alreadyRejected_throwsConflict() {
    reservation.setStatus(ReservationStatus.REJECTED);
    givenReservation();

    Throwable ex = catchThrowable(() -> approvalFlowService.reject(4L, COORDINATOR, "Motiv"));

    assertApiError(ex, HttpStatus.CONFLICT, "CERERE_FARA_DECIZIE_IN_ASTEPTARE");
  }

  // ───────────── findForApprover ─────────────

  @Test
  void findForApprover_pending_passesGrantsAndLaboratoriesOfUser() {
    AuthenticatedUser both =
        user(
            5L,
            role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID),
            role(Role.LAB_MANAGER, ScopeType.LABORATORY, 1L),
            role(Role.STUDENT, ScopeType.GLOBAL, null));
    List<ReservationDetailResponse> pending = List.of();
    given(reservationService.findAwaitingDecision(Set.of(GRANT_ID), Set.of(1L)))
        .willReturn(pending);

    assertThat(approvalFlowService.findForApprover(both, ApprovalListStatus.PENDING))
        .isSameAs(pending);
  }

  @Test
  void findForApprover_rejected_returnsReservationsRejectedByUser() {
    given(approvalRepository.findReservationIdsDecidedBy(5L, ApprovalDecision.REJECTED))
        .willReturn(List.of(5L));
    List<ReservationDetailResponse> rejected = List.of();
    given(reservationService.findDetails(List.of(5L))).willReturn(rejected);

    assertThat(approvalFlowService.findForApprover(COORDINATOR, ApprovalListStatus.REJECTED))
        .isSameAs(rejected);
  }

  // ───────────── date de test ─────────────

  /** Rezervarea 4, pe grantul 2, la o resursă din laboratorul 2. */
  private static Reservation reservation(long requesterId, Instant start) {
    User requester = withId(new User("Solicitant", "solicitant@usv.ro", "hash"), requesterId);
    Laboratory laboratory =
        withId(new Laboratory("Laborator Electronică", "Corp C, sala C201", 3L), LABORATORY_ID);
    ResourceCategory category = withId(new ResourceCategory("Echipament"), 2L);
    Resource resource = withId(new Resource(laboratory, category, "Osciloscop Rigol DS1054"), 4L);
    Reservation reservation =
        new Reservation(
            requester,
            resource,
            GRANT_ID,
            start,
            start.plus(Duration.ofMinutes(90)),
            new BigDecimal("1.50"),
            "Măsurători pe placa de achiziție DigiLab");
    reservation.setStatus(ReservationStatus.PENDING_COORDINATOR);
    return withId(reservation, 4L);
  }

  private void givenReservation() {
    given(reservationService.getForUpdate(4L)).willReturn(reservation);
  }

  /** {@code saveAndFlush} întoarce aprobarea cu id și cu aprobatorul cerut. */
  private void givenSaveReturnsApproval() {
    given(userService.getUser(anyLong()))
        .willAnswer(invocation ->
            withId(new User("Aprobator", "aprobator@usv.ro", "hash"), invocation.getArgument(0)));
    given(approvalRepository.saveAndFlush(any(Approval.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 50L));
  }

  private static AuthenticatedUser user(long userId, ScopedRole... roles) {
    return new AuthenticatedUser(userId, SESSION_ID, List.of(roles));
  }

  private static ScopedRole role(Role role, ScopeType scopeType, Long scopeId) {
    return new ScopedRole(role, scopeType, scopeId);
  }

  /** Id-ul este generat de baza de date și nu are setter; în testele unitare îl setăm direct. */
  private static <T> T withId(T entity, Long id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  private static void assertApiError(Throwable ex, HttpStatus status, String code) {
    assertThat(ex).isInstanceOf(ApiException.class);
    ApiException apiException = (ApiException) ex;
    assertThat(apiException.getStatus()).isEqualTo(status);
    assertThat(apiException.getCode()).isEqualTo(code);
  }
}
