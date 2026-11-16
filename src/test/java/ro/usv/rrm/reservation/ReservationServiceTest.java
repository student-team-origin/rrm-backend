package ro.usv.rrm.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.usv.rrm.approval.ApprovalDecision;
import ro.usv.rrm.approval.ApprovalService;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.ScopedRole;
import ro.usv.rrm.common.error.ApiError;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.grant.GrantService;
import ro.usv.rrm.grant.dto.GrantResponse;
import ro.usv.rrm.laboratory.Laboratory;
import ro.usv.rrm.reservation.dto.CreateReservationRequest;
import ro.usv.rrm.reservation.dto.OccupiedIntervalResponse;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;
import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.resource.ResourceCategory;
import ro.usv.rrm.resource.ResourceService;
import ro.usv.rrm.resource.ResourceStatus;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.User;
import ro.usv.rrm.user.UserService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final long REQUESTER_ID = 6L;
  private static final long OTHER_USER_ID = 7L;
  private static final long GRANT_ID = 1L;
  private static final long LABORATORY_ID = 1L;
  private static final UUID SESSION_ID = UUID.fromString("5f0c6b9e-6f3a-4c55-9d43-1d2f1f9f2a10");

  /** Mâine, 10:00 ora României (08:00 UTC). */
  private static final OffsetDateTime TOMORROW_10 =
      OffsetDateTime.parse("2026-11-16T10:00:00+02:00");

  @Mock private ReservationRepository reservationRepository;
  @Mock private ApprovalService approvalService;
  @Mock private GrantService grantService;
  @Mock private ResourceService resourceService;
  @Mock private UserService userService;

  private ReservationService reservationService;
  private Reservation reservation;

  @BeforeEach
  void setUp() {
    reservationService =
        new ReservationService(
            reservationRepository,
            approvalService,
            grantService,
            resourceService,
            userService,
            Clock.fixed(NOW, ZoneOffset.UTC));
    reservation = reservationStartingAt(NOW.plus(Duration.ofDays(1)));
  }

  // ───────────── create ─────────────

  @Test
  void create_freeSlot_savesPendingCoordinatorRequest() {
    final Resource resource = givenResource(ResourceStatus.ACTIVE, 30);
    givenGrantWithRemainingHours("114.00");
    given(userService.getUser(REQUESTER_ID)).willReturn(requester());
    given(reservationRepository.saveAndFlush(any(Reservation.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 200L));

    ReservationResponse created =
        reservationService.create(request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID);

    assertThat(created.id()).isEqualTo(200L);
    assertThat(created.stare()).isEqualTo(ReservationStatus.PENDING_COORDINATOR);
    assertThat(created.oreSolicitate()).isEqualByComparingTo("2.00");
    assertThat(created.resursaId()).isEqualTo(resource.getId());
    assertThat(created.solicitantId()).isEqualTo(REQUESTER_ID);
    assertThat(created.scop()).isEqualTo("Imprimarea carcasei");
    // Intervalul verificat include timpul de pregătire al resursei (30 de minute).
    then(reservationRepository)
        .should()
        .findBlocking(
            5L,
            ReservationStatus.BLOCKING,
            Instant.parse("2026-11-16T08:00:00Z"),
            Instant.parse("2026-11-16T10:30:00Z"));
  }

  @Test
  void create_partialHour_roundsRequestedHoursUp() {
    givenResource(ResourceStatus.ACTIVE, 0);
    givenGrantWithRemainingHours("114.00");
    given(userService.getUser(REQUESTER_ID)).willReturn(requester());
    given(reservationRepository.saveAndFlush(any(Reservation.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 200L));

    ReservationResponse created =
        reservationService.create(request(TOMORROW_10, TOMORROW_10.plusMinutes(20)), REQUESTER_ID);

    assertThat(created.oreSolicitate()).isEqualByComparingTo("0.34");
  }

  @Test
  void create_overlappingSlot_throwsConflictWithFreeIntervalsOfThatDay() {
    givenResource(ResourceStatus.ACTIVE, 30);
    givenGrantWithRemainingHours("114.00");
    // Ocupat 09:00–11:00 UTC, blocat până la 11:30 cu timpul de pregătire.
    given(reservationRepository.findBlocking(eq(5L), any(), any(), any()))
        .willReturn(
            List.of(
                blocking(
                    Instant.parse("2026-11-16T09:00:00Z"),
                    Instant.parse("2026-11-16T11:00:00Z"),
                    Duration.ofMinutes(30))));

    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.CONFLICT, "INTERVAL_OCUPAT");
    // Ziua de 16 noiembrie după fusul orar al cererii (+02:00): 15 nov. 22:00 – 16 nov. 22:00 UTC.
    // Primul interval se termină cu 30 de minute (timpul de pregătire) înaintea rezervării.
    assertThat(((ApiException) ex).getFreeIntervals())
        .containsExactly(
            new ApiError.FreeInterval(
                Instant.parse("2026-11-15T22:00:00Z"), Instant.parse("2026-11-16T08:30:00Z")),
            new ApiError.FreeInterval(
                Instant.parse("2026-11-16T11:30:00Z"), Instant.parse("2026-11-16T22:00:00Z")));
    then(reservationRepository)
        .should()
        .findBlocking(
            5L,
            ReservationStatus.BLOCKING,
            Instant.parse("2026-11-15T22:00:00Z"),
            Instant.parse("2026-11-16T22:30:00Z"));
    then(reservationRepository).should(never()).saveAndFlush(any());
  }

  @Test
  void create_overlappingSlotToday_freeIntervalsStartFromNow() {
    givenResource(ResourceStatus.ACTIVE, 0);
    givenGrantWithRemainingHours("114.00");
    given(reservationRepository.findBlocking(eq(5L), any(), any(), any()))
        .willReturn(
            List.of(
                blocking(
                    Instant.parse("2026-11-15T13:00:00Z"),
                    Instant.parse("2026-11-15T15:00:00Z"),
                    Duration.ZERO)));
    OffsetDateTime start = OffsetDateTime.parse("2026-11-15T14:00:00Z");

    Throwable ex =
        catchThrowable(
            () -> reservationService.create(request(start, start.plusHours(1)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.CONFLICT, "INTERVAL_OCUPAT");
    assertThat(((ApiException) ex).getFreeIntervals())
        .containsExactly(
            new ApiError.FreeInterval(NOW, Instant.parse("2026-11-15T13:00:00Z")),
            new ApiError.FreeInterval(
                Instant.parse("2026-11-15T15:00:00Z"), Instant.parse("2026-11-16T00:00:00Z")));
  }

  @Test
  void create_overlapRejectedByDatabase_throwsConflict() {
    givenResource(ResourceStatus.ACTIVE, 30);
    givenGrantWithRemainingHours("114.00");
    given(userService.getUser(REQUESTER_ID)).willReturn(requester());
    given(reservationRepository.saveAndFlush(any(Reservation.class)))
        .willThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException(
                    "ERROR: conflicting key value violates exclusion constraint"
                        + " \"rezervare_fara_suprapunere\"",
                    "23P01")));

    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.CONFLICT, "INTERVAL_OCUPAT");
    assertThat(((ApiException) ex).getFreeIntervals()).isNull();
  }

  @Test
  void create_otherIntegrityViolation_isRethrown() {
    givenResource(ResourceStatus.ACTIVE, 30);
    givenGrantWithRemainingHours("114.00");
    given(userService.getUser(REQUESTER_ID)).willReturn(requester());
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "fk", new SQLException("violates foreign key constraint", "23503"));
    given(reservationRepository.saveAndFlush(any(Reservation.class))).willThrow(violation);

    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID));

    assertThat(ex).isSameAs(violation);
  }

  @Test
  void create_insufficientGrantBalance_throwsUnprocessable() {
    givenResource(ResourceStatus.ACTIVE, 30);
    givenGrantWithRemainingHours("1.50");

    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "SOLD_INSUFICIENT");
    assertThat(ex)
        .hasMessage("Grantul mai are 1,5 ore disponibile, iar rezervarea necesită 2 ore.");
    then(reservationRepository).should(never()).saveAndFlush(any());
  }

  @Test
  void create_exactlyRemainingHours_isAllowed() {
    givenResource(ResourceStatus.ACTIVE, 0);
    givenGrantWithRemainingHours("2.00");
    given(userService.getUser(REQUESTER_ID)).willReturn(requester());
    given(reservationRepository.saveAndFlush(any(Reservation.class)))
        .willAnswer(invocation -> withId(invocation.getArgument(0), 200L));

    ReservationResponse created =
        reservationService.create(request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID);

    assertThat(created.oreSolicitate()).isEqualByComparingTo("2.00");
  }

  @ParameterizedTest
  @EnumSource(names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
  void create_unavailableResource_throwsUnprocessable(ResourceStatus status) {
    givenResource(status, 0);
    givenGrantWithRemainingHours("114.00");

    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(2)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.UNPROCESSABLE_CONTENT, "RESURSA_INDISPONIBILA");
    then(reservationRepository).should(never()).saveAndFlush(any());
  }

  @Test
  void create_endNotAfterStart_throwsBadRequestWithoutLocking() {
    Throwable ex =
        catchThrowable(
            () -> reservationService.create(request(TOMORROW_10, TOMORROW_10), REQUESTER_ID));

    assertApiError(ex, HttpStatus.BAD_REQUEST, "INTERVAL_INVALID");
    then(grantService).should(never()).getBalanceForUpdate(anyLong());
  }

  @Test
  void create_startInPast_throwsBadRequest() {
    OffsetDateTime start = NOW.minus(Duration.ofMinutes(1)).atOffset(ZoneOffset.UTC);

    Throwable ex =
        catchThrowable(
            () -> reservationService.create(request(start, start.plusHours(1)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.BAD_REQUEST, "INTERVAL_INVALID");
  }

  @Test
  void create_longerThanMaximumHours_throwsBadRequest() {
    Throwable ex =
        catchThrowable(() ->
            reservationService.create(
                request(TOMORROW_10, TOMORROW_10.plusHours(1000)), REQUESTER_ID));

    assertApiError(ex, HttpStatus.BAD_REQUEST, "INTERVAL_INVALID");
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

  // ───────────── findAwaitingDecision / findDetails ─────────────

  @Test
  @SuppressWarnings("unchecked")
  void findAwaitingDecision_noGrantsAndNoLaboratories_returnsEmptyWithoutQuery() {
    assertThat(reservationService.findAwaitingDecision(Set.of(), Set.of())).isEmpty();

    then(reservationRepository).should(never()).findAll(any(Specification.class), any(Sort.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void findAwaitingDecision_withScopes_returnsDetailsWithApprovals() {
    given(reservationRepository.findAll(any(Specification.class), any(Sort.class)))
        .willReturn(List.of(reservation));

    ApprovalResponse approval =
        new ApprovalResponse(
            1L, 100L, 4L, 1, ApprovalDecision.APPROVED, null, NOW.minus(Duration.ofHours(1)));
    given(approvalService.findByReservations(List.of(100L)))
        .willReturn(Map.of(100L, List.of(approval)));

    List<ReservationDetailResponse> pending =
        reservationService.findAwaitingDecision(Set.of(GRANT_ID), Set.of());

    assertThat(pending).extracting(ReservationDetailResponse::id).containsExactly(100L);
    assertThat(pending.getFirst().aprobari()).containsExactly(approval);
  }

  @Test
  void findDetails_keepsRequestedOrderAndSkipsMissingIds() {
    Reservation other = withId(reservationStartingAt(NOW.plus(Duration.ofDays(2))), 101L);
    given(reservationRepository.findWithDetailsByIdIn(List.of(101L, 999L, 100L)))
        .willReturn(List.of(reservation, other));
    given(approvalService.findByReservations(List.of(101L, 100L))).willReturn(Map.of());

    List<ReservationDetailResponse> details =
        reservationService.findDetails(List.of(101L, 999L, 100L));

    assertThat(details).extracting(ReservationDetailResponse::id).containsExactly(101L, 100L);
    assertThat(details).allSatisfy(detail -> assertThat(detail.aprobari()).isEmpty());
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

  // ───────────── findOccupiedIntervals ─────────────

  private static final Instant WEEK_START = Instant.parse("2026-11-15T22:00:00Z");

  @Test
  void findOccupiedIntervals_returnsBlockingReservationsWithPreparationTime() {
    Reservation other =
        blocking(
            Instant.parse("2026-11-16T09:00:00Z"),
            Instant.parse("2026-11-16T11:00:00Z"),
            Duration.ofMinutes(15));
    other.setStatus(ReservationStatus.APPROVED);
    Instant weekEnd = WEEK_START.plus(Duration.ofDays(7));
    given(reservationRepository.findBlocking(5L, ReservationStatus.BLOCKING, WEEK_START, weekEnd))
        .willReturn(List.of(other));

    List<OccupiedIntervalResponse> intervals =
        reservationService.findOccupiedIntervals(5L, WEEK_START, weekEnd);

    assertThat(intervals)
        .containsExactly(
            new OccupiedIntervalResponse(
                Instant.parse("2026-11-16T09:00:00Z"),
                Instant.parse("2026-11-16T11:15:00Z"),
                ReservationStatus.APPROVED));
    then(resourceService).should().findById(5L);
  }

  @Test
  void findOccupiedIntervals_unknownResource_throwsNotFound() {
    given(resourceService.findById(999L))
        .willThrow(ApiException.notFound("RESURSA_INEXISTENTA", "Resursa nu există."));

    Throwable ex =
        catchThrowable(() ->
            reservationService.findOccupiedIntervals(
                999L, WEEK_START, WEEK_START.plus(Duration.ofDays(7))));

    assertApiError(ex, HttpStatus.NOT_FOUND, "RESURSA_INEXISTENTA");
    then(reservationRepository).should(never()).findBlocking(any(), any(), any(), any());
  }

  @Test
  void findOccupiedIntervals_toNotAfterFrom_throwsBadRequest() {
    Throwable ex =
        catchThrowable(() -> reservationService.findOccupiedIntervals(5L, WEEK_START, WEEK_START));

    assertApiError(ex, HttpStatus.BAD_REQUEST, "INTERVAL_INVALID");
    then(resourceService).should(never()).findById(any());
  }

  @Test
  void findOccupiedIntervals_longerThanMaximum_throwsBadRequest() {
    Instant to = WEEK_START.plus(Duration.ofDays(ReservationService.MAX_AVAILABILITY_DAYS + 1L));

    Throwable ex =
        catchThrowable(() -> reservationService.findOccupiedIntervals(5L, WEEK_START, to));

    assertApiError(ex, HttpStatus.BAD_REQUEST, "INTERVAL_INVALID");
  }

  @Test
  void findOccupiedIntervals_exactlyMaximum_isAllowed() {
    Instant to = WEEK_START.plus(Duration.ofDays(ReservationService.MAX_AVAILABILITY_DAYS));

    assertThat(reservationService.findOccupiedIntervals(5L, WEEK_START, to)).isEmpty();
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

  private static CreateReservationRequest request(OffsetDateTime start, OffsetDateTime end) {
    return new CreateReservationRequest(5L, GRANT_ID, start, end, "  Imprimarea carcasei ");
  }

  private static User requester() {
    return withId(
        new User("Andrei Popescu", "andrei.popescu@student.usv.ro", "hash"), REQUESTER_ID);
  }

  /** Resursa 5, blocată pentru cerere, cu starea și timpul de pregătire date. */
  private Resource givenResource(ResourceStatus status, int preparationMinutes) {
    Resource resource = reservation.getResource();
    resource.setStatus(status);
    resource.setPreparationTimeMinutes(preparationMinutes);
    given(resourceService.getForUpdate(5L)).willReturn(resource);
    return resource;
  }

  private void givenGrantWithRemainingHours(String remaining) {
    BigDecimal remainingHours = new BigDecimal(remaining);
    given(grantService.getBalanceForUpdate(GRANT_ID))
        .willReturn(
            new GrantResponse(
                GRANT_ID,
                "PN-III-P4-2026",
                4L,
                new BigDecimal("120.00"),
                new BigDecimal("120.00").subtract(remainingHours),
                remainingHours,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-12-31")));
  }

  /** O rezervare activă a altui utilizator pe resursa 5; sfârșitul blocat îl calculează DB-ul. */
  private Reservation blocking(Instant start, Instant end, Duration preparation) {
    Reservation other =
        new Reservation(
            requester(),
            reservation.getResource(),
            GRANT_ID,
            start,
            end,
            new BigDecimal("2.00"),
            "Alt proiect");
    ReflectionTestUtils.setField(other, "blockedEnd", end.plus(preparation));
    return other;
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
