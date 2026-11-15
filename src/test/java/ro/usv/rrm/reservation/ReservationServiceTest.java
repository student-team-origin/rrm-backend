package ro.usv.rrm.reservation;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.approval.ApprovalDecision;
import ro.usv.rrm.approval.ApprovalService;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.ScopedRole;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.laboratory.Laboratory;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;
import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.resource.ResourceCategory;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.User;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final long REQUESTER_ID = 6L;
  private static final long OTHER_USER_ID = 7L;
  private static final long GRANT_ID = 1L;
  private static final long LABORATORY_ID = 1L;
  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");

  @Mock private ReservationRepository reservationRepository;
  @Mock private ApprovalService approvalService;

  private ReservationService reservationService;
  private Reservation reservation;

  @BeforeEach
  void setUp() {
    reservationService =
        new ReservationService(
            reservationRepository, approvalService, Clock.fixed(NOW, ZoneOffset.UTC));
    reservation = reservationStartingAt(NOW.plus(Duration.ofDays(1)));
  }

  // ───────────── findMine ─────────────

  @Test
  @SuppressWarnings("unchecked")
  void findMine_hugePageSize_clampsToMaximum() {
    given(reservationRepository.findAll(any(Specification.class), any(Pageable.class)))
        .willAnswer(
            invocation -> new PageImpl<>(List.of(reservation), invocation.getArgument(1), 1));

    PageResponse<ReservationResponse> page =
        reservationService.findMine(
            REQUESTER_ID, new ReservationFilter(null, null, null), -3, 1000);

    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(ReservationService.MAX_PAGE_SIZE);
    assertThat(page.items()).extracting(ReservationResponse::id).containsExactly(100L);
  }

  // ───────────── findById ─────────────

  @Test
  void findById_requester_returnsDetailWithApprovals() {
    given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
    ApprovalResponse approval =
        new ApprovalResponse(
            1L, 100L, 4L, 1, ApprovalDecision.APPROVED, null, NOW.minus(Duration.ofHours(1)));
    given(approvalService.findByReservation(100L)).willReturn(List.of(approval));

    ReservationDetailResponse detail = reservationService.findById(100L, user(REQUESTER_ID));

    assertThat(detail.id()).isEqualTo(100L);
    assertThat(detail.stare()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
    assertThat(detail.resursa().denumire()).isEqualTo("Imprimantă 3D Prusa MK4");
    assertThat(detail.solicitant().id()).isEqualTo(REQUESTER_ID);
    assertThat(detail.aprobari()).containsExactly(approval);
  }

  @Test
  void findById_coordinatorOfReservationGrant_returnsDetail() {
    AuthenticatedUser other =
        givenRoles(OTHER_USER_ID, role(Role.GRANT_COORDINATOR, ScopeType.GRANT, GRANT_ID));

    assertThat(reservationService.findById(100L, other).id()).isEqualTo(100L);
  }

  @Test
  void findById_managerOfResourceLaboratory_returnsDetail() {
    AuthenticatedUser other =
        givenRoles(OTHER_USER_ID, role(Role.LAB_MANAGER, ScopeType.LABORATORY, LABORATORY_ID));

    assertThat(reservationService.findById(100L, other).id()).isEqualTo(100L);
  }

  @Test
  void findById_systemAdministrator_returnsDetail() {
    AuthenticatedUser other =
        givenRoles(OTHER_USER_ID, role(Role.SYSTEM_ADMIN, ScopeType.GLOBAL, null));

    assertThat(reservationService.findById(100L, other).id()).isEqualTo(100L);
  }

  @Test
  void findById_userWhoAlreadyDecided_returnsDetail() {
    AuthenticatedUser other = givenRoles(OTHER_USER_ID, role(Role.STUDENT, ScopeType.GLOBAL, null));
    given(approvalService.hasDecided(100L, OTHER_USER_ID)).willReturn(true);

    assertThat(reservationService.findById(100L, other).id()).isEqualTo(100L);
  }

  @Test
  void findById_otherStudent_throwsForbidden() {
    AuthenticatedUser other = givenRoles(OTHER_USER_ID, role(Role.STUDENT, ScopeType.GLOBAL, null));

    Throwable ex = catchThrowable(() -> reservationService.findById(100L, other));

    assertApiError(ex, HttpStatus.FORBIDDEN, "REZERVARE_INACCESIBILA");
    then(approvalService).should(never()).findByReservation(anyLong());
  }

  @Test
  void findById_rolesScopedToOtherGrantAndLaboratory_throwsForbidden() {
    AuthenticatedUser other =
        givenRoles(
            OTHER_USER_ID,
            role(Role.GRANT_COORDINATOR, ScopeType.GRANT, 2L),
            role(Role.LAB_MANAGER, ScopeType.LABORATORY, 2L));

    Throwable ex = catchThrowable(() -> reservationService.findById(100L, other));

    assertApiError(ex, HttpStatus.FORBIDDEN, "REZERVARE_INACCESIBILA");
  }

  @Test
  void findById_unknownId_throwsNotFound() {
    given(reservationRepository.findById(999L)).willReturn(Optional.empty());

    Throwable ex = catchThrowable(() -> reservationService.findById(999L, user(REQUESTER_ID)));

    assertApiError(ex, HttpStatus.NOT_FOUND, "REZERVARE_INEXISTENTA");
  }

  // ───────────── cancel ─────────────

  @ParameterizedTest
  @EnumSource(names = {"PENDING_COORDINATOR", "PENDING_LABORATORY", "APPROVED"})
  void cancel_cancellableStatusBeforeStart_setsCancelled(ReservationStatus status) {
    reservation.setStatus(status);
    given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

    ReservationResponse response =
        reservationService.cancel(100L, REQUESTER_ID, "Nu mai am nevoie");

    assertThat(response.stare()).isEqualTo(ReservationStatus.CANCELLED);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
  }

  @Test
  void cancel_afterStart_throwsConflict() {
    reservation = reservationStartingAt(NOW.minus(Duration.ofMinutes(1)));
    reservation.setStatus(ReservationStatus.APPROVED);
    given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

    Throwable ex = catchThrowable(() -> reservationService.cancel(100L, REQUESTER_ID, null));

    assertApiError(ex, HttpStatus.CONFLICT, "REZERVARE_INCEPUTA");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  void cancel_exactlyAtStart_throwsConflict() {
    reservation = reservationStartingAt(NOW);
    given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

    Throwable ex = catchThrowable(() -> reservationService.cancel(100L, REQUESTER_ID, null));

    assertApiError(ex, HttpStatus.CONFLICT, "REZERVARE_INCEPUTA");
  }

  @ParameterizedTest
  @EnumSource(
      names = {"PENDING_COORDINATOR", "PENDING_LABORATORY", "APPROVED"},
      mode = EnumSource.Mode.EXCLUDE)
  void cancel_nonCancellableStatus_throwsConflict(ReservationStatus status) {
    reservation.setStatus(status);
    given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

    Throwable ex = catchThrowable(() -> reservationService.cancel(100L, REQUESTER_ID, null));

    assertApiError(ex, HttpStatus.CONFLICT, "ANULARE_NEPERMISA");
    assertThat(reservation.getStatus()).isEqualTo(status);
  }

  @Test
  void cancel_otherUsersReservation_throwsForbidden() {
    given(reservationRepository.findByIdForUpdate(100L)).willReturn(Optional.of(reservation));

    Throwable ex = catchThrowable(() -> reservationService.cancel(100L, OTHER_USER_ID, null));

    assertApiError(ex, HttpStatus.FORBIDDEN, "REZERVARE_ALTUI_UTILIZATOR");
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_LABORATORY);
  }

  @Test
  void cancel_unknownId_throwsNotFound() {
    given(reservationRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

    Throwable ex = catchThrowable(() -> reservationService.cancel(999L, REQUESTER_ID, null));

    assertApiError(ex, HttpStatus.NOT_FOUND, "REZERVARE_INEXISTENTA");
  }

  // ───────────── date de test ─────────────

  /** Rezervarea 100 a utilizatorului 6, pe grantul 1, la o resursă din laboratorul 1. */
  private static Reservation reservationStartingAt(Instant start) {
    User requester =
        withId(new User("Andrei Popescu", "andrei.popescu@student.usv.ro", "hash"), REQUESTER_ID);
    Laboratory laboratory =
        withId(new Laboratory("Laborator Robotică", "Corp C, sala C204", 2L), LABORATORY_ID);
    ResourceCategory category = withId(new ResourceCategory("Echipament"), 2L);
    Resource resource = withId(new Resource(laboratory, category, "Imprimantă 3D Prusa MK4"), 5L);
    Reservation reservation =
        new Reservation(
            requester,
            resource,
            GRANT_ID,
            start,
            start.plus(Duration.ofHours(2)),
            new BigDecimal("2.00"),
            "Imprimarea carcasei pentru senzor");
    reservation.setStatus(ReservationStatus.PENDING_LABORATORY);
    return withId(reservation, 100L);
  }

  /** Rezervarea 100 există, iar utilizatorul dat o cere cu rolurile date. */
  private AuthenticatedUser givenRoles(long userId, ScopedRole... roles) {
    given(reservationRepository.findById(100L)).willReturn(Optional.of(reservation));
    return new AuthenticatedUser(userId, SESSION_ID, List.of(roles));
  }

  private static AuthenticatedUser user(long userId) {
    return new AuthenticatedUser(userId, SESSION_ID, List.of());
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
