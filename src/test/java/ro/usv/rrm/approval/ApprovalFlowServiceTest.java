package ro.usv.rrm.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.approval.dto.ApprovalResponse;
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
import ro.usv.rrm.user.dto.UserResponse;
import ro.usv.rrm.user.dto.UserRoleResponse;

@ExtendWith(MockitoExtension.class)
class ApprovalFlowServiceTest {

  private static final long GRANT_ID = 2L;
  private static final long LABORATORY_ID = 2L;
  private static final long COORDINATOR_ID = 5L;
  private static final long LAB_MANAGER_ID = 3L;
  private static final long STUDENT_ID = 6L;

  @Mock private ApprovalRepository approvalRepository;
  @Mock private ReservationService reservationService;
  @Mock private UserService userService;

  @InjectMocks private ApprovalFlowService approvalFlowService;

  private Reservation reservation;

  @BeforeEach
  void setUp() {
    reservation = reservation(ReservationStatus.PENDING_COORDINATOR);
  }

  // ───────────── approve ─────────────

  @Test
  void approve_coordinatorAtLevel1_sendsRequestToLaboratory() {
    givenReservationAndUser(
        COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));
    givenSaveReturnsApproval();

    ApprovalResponse response =
        approvalFlowService.approve(4L, COORDINATOR_ID, "Încadrat în buget");

    assertThat(response.nivel()).isEqualTo(Approval.COORDINATOR_LEVEL);
    assertThat(response.decizie()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(response.aprobatorId()).isEqualTo(COORDINATOR_ID);
    assertThat(response.observatie()).isEqualTo("Încadrat în buget");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
  }

  @Test
  void approve_labManagerAtLevel2_approvesReservation() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservationAndUser(
        LAB_MANAGER_ID, role(Role.LAB_MANAGER, ScopeType.LABORATORY, LABORATORY_ID));
    givenSaveReturnsApproval();

    ApprovalResponse response = approvalFlowService.approve(4L, LAB_MANAGER_ID, null);

    assertThat(response.nivel()).isEqualTo(Approval.LABORATORY_LEVEL);
    assertThat(response.decizie()).isEqualTo(ApprovalDecision.APPROVED);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void approve_labManagerAtLevel1_throwsForbidden() {
    givenReservationAndUser(
        LAB_MANAGER_ID, role(Role.LAB_MANAGER, ScopeType.LABORATORY, LABORATORY_ID));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, LAB_MANAGER_ID, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_COORDINATOR);
    then(approvalRepository).should(never()).saveAndFlush(any());
  }

  @Test
  void approve_coordinatorAtLevel2_throwsForbidden() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservationAndUser(
        COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR_ID, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
  }

  @Test
  void approve_coordinatorOfAnotherGrant_throwsForbidden() {
    givenReservationAndUser(COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, 1L));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR_ID, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
  }

  @Test
  void approve_student_throwsForbidden() {
    givenReservationAndUser(STUDENT_ID, role(Role.STUDENT, ScopeType.GLOBAL, null));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, STUDENT_ID, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_DREPT_APROBARE");
  }

  @ParameterizedTest
  @EnumSource(
      names = {"PENDING_COORDINATOR", "PENDING_LABORATORY"},
      mode = EnumSource.Mode.EXCLUDE)
  void approve_notAwaitingDecision_throwsConflict(ReservationStatus status) {
    reservation.setStatus(status);
    givenReservationAndUser(
        COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(4L, COORDINATOR_ID, null));

    assertApiError(ex, HttpStatus.CONFLICT, "CERERE_FARA_DECIZIE_IN_ASTEPTARE");
    assertThat(reservation.getStatus()).isEqualTo(status);
  }

  @Test
  void approve_unknownReservation_throwsNotFound() {
    given(reservationService.getForUpdate(999L))
        .willThrow(ApiException.notFound("REZERVARE_INEXISTENTA", "Nu există."));

    Throwable ex = catchThrowable(() -> approvalFlowService.approve(999L, COORDINATOR_ID, null));

    assertApiError(ex, HttpStatus.NOT_FOUND, "REZERVARE_INEXISTENTA");
  }

  // ───────────── reject ─────────────

  @Test
  void reject_coordinatorAtLevel1_rejectsWithReason() {
    givenReservationAndUser(
        COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));
    givenSaveReturnsApproval();

    ApprovalResponse response =
        approvalFlowService.reject(4L, COORDINATOR_ID, "Nu ține de obiectivele grantului");

    assertThat(response.decizie()).isEqualTo(ApprovalDecision.REJECTED);
    assertThat(response.nivel()).isEqualTo(Approval.COORDINATOR_LEVEL);
    assertThat(response.observatie()).isEqualTo("Nu ține de obiectivele grantului");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
  }

  @Test
  void reject_labManagerAtLevel2_rejectsReservation() {
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    givenReservationAndUser(
        LAB_MANAGER_ID, role(Role.LAB_MANAGER, ScopeType.LABORATORY, LABORATORY_ID));
    givenSaveReturnsApproval();

    ApprovalResponse response = approvalFlowService.reject(4L, LAB_MANAGER_ID, "Resursa e ocupată");

    assertThat(response.nivel()).isEqualTo(Approval.LABORATORY_LEVEL);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
  }

  @Test
  void reject_alreadyRejected_throwsConflict() {
    reservation.setStatus(ReservationStatus.REJECTED);
    givenReservationAndUser(
        COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));

    Throwable ex = catchThrowable(() -> approvalFlowService.reject(4L, COORDINATOR_ID, "Motiv"));

    assertApiError(ex, HttpStatus.CONFLICT, "CERERE_FARA_DECIZIE_IN_ASTEPTARE");
  }

  // ───────────── findForApprover ─────────────

  @Test
  void findForApprover_pending_passesGrantsAndLaboratoriesOfUser() {
    givenRoles(
        COORDINATOR_ID,
        role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID),
        role(Role.LAB_MANAGER, ScopeType.LABORATORY, 1L),
        role(Role.STUDENT, ScopeType.GLOBAL, null));
    List<ReservationDetailResponse> pending = List.of();
    given(reservationService.findAwaitingDecision(Set.of(GRANT_ID), Set.of(1L)))
        .willReturn(pending);

    assertThat(approvalFlowService.findForApprover(COORDINATOR_ID, ApprovalListStatus.PENDING))
        .isSameAs(pending);
  }

  @Test
  void findForApprover_rejected_returnsReservationsRejectedByUser() {
    givenRoles(COORDINATOR_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));
    given(approvalRepository.findReservationIdsDecidedBy(COORDINATOR_ID, ApprovalDecision.REJECTED))
        .willReturn(List.of(5L));
    List<ReservationDetailResponse> rejected = List.of();
    given(reservationService.findDetails(List.of(5L))).willReturn(rejected);

    assertThat(approvalFlowService.findForApprover(COORDINATOR_ID, ApprovalListStatus.REJECTED))
        .isSameAs(rejected);
  }

  @Test
  void findForApprover_student_throwsForbidden() {
    givenRoles(STUDENT_ID, role(Role.STUDENT, ScopeType.GLOBAL, null));

    Throwable ex =
        catchThrowable(
            () -> approvalFlowService.findForApprover(STUDENT_ID, ApprovalListStatus.PENDING));

    assertApiError(ex, HttpStatus.FORBIDDEN, "FARA_ROL_APROBATOR");
    then(reservationService).should(never()).findAwaitingDecision(any(), any());
  }

  // ───────────── date de test ─────────────

  /** Rezervarea 4 a utilizatorului 8, pe grantul 2, la o resursă din laboratorul 2. */
  private static Reservation reservation(ReservationStatus status) {
    User requester = withId(new User("Ștefan Rusu", "stefan.rusu@student.usv.ro", "hash"), 8L);
    Laboratory laboratory =
        withId(new Laboratory("Laborator Electronică", "Corp C, sala C201", 3L), LABORATORY_ID);
    ResourceCategory category = withId(new ResourceCategory("Echipament"), 2L);
    Resource resource = withId(new Resource(laboratory, category, "Osciloscop Rigol DS1054"), 4L);
    Instant start = Instant.parse("2026-11-17T07:00:00Z");
    Reservation reservation =
        new Reservation(
            requester,
            resource,
            GRANT_ID,
            start,
            start.plus(Duration.ofMinutes(90)),
            new BigDecimal("1.50"),
            "Măsurători pe placa de achiziție DigiLab");
    reservation.setStatus(status);
    return withId(reservation, 4L);
  }

  private void givenReservationAndUser(long userId, UserRoleResponse... roles) {
    given(reservationService.getForUpdate(4L)).willReturn(reservation);
    givenRoles(userId, roles);
  }

  private void givenRoles(long userId, UserRoleResponse... roles) {
    given(userService.findById(userId))
        .willReturn(
            new UserResponse(userId, "Utilizator", "user@usv.ro", true, false, List.of(roles)));
  }

  /** {@code saveAndFlush} întoarce aprobarea cu id și cu aprobatorul cerut. */
  private void givenSaveReturnsApproval() {
    given(userService.getUser(anyLong()))
        .willAnswer(invocation ->
            withId(new User("Aprobator", "aprobator@usv.ro", "hash"), invocation.getArgument(0)));
    given(approvalRepository.saveAndFlush(any(Approval.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 50L));
  }

  private static UserRoleResponse role(Role role, ScopeType scopeType, Long scopeId) {
    return new UserRoleResponse(role, scopeType, scopeId);
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
