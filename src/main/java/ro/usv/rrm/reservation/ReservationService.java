package ro.usv.rrm.reservation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.ApprovalService;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.common.error.ApiError;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.grant.GrantService;
import ro.usv.rrm.grant.dto.GrantResponse;
import ro.usv.rrm.reservation.dto.CreateReservationRequest;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;
import ro.usv.rrm.resource.Resource;
import ro.usv.rrm.resource.ResourceService;
import ro.usv.rrm.resource.ResourceStatus;
import ro.usv.rrm.user.UserService;

/** Rezervările: crearea cererii (hard lock), „Rezervările mele”, detaliul și anularea. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

  static final int MAX_PAGE_SIZE = 100;

  /** Limita coloanei {@code ore_solicitate NUMERIC(5,2)}. */
  private static final BigDecimal MAX_REQUESTED_HOURS = new BigDecimal("999.99");

  private static final String OVERLAP_CONSTRAINT = "rezervare_fara_suprapunere";

  private static final String EXCLUSION_VIOLATION = "23P01";
  private static final BigDecimal MILLIS_PER_HOUR =
      BigDecimal.valueOf(Duration.ofHours(1).toMillis());

  private final ReservationRepository reservationRepository;
  private final ApprovalService approvalService;
  private final GrantService grantService;
  private final ResourceService resourceService;
  private final UserService userService;
  private final Clock clock;

  /**
   * Rezervările cerute de utilizatorul dat, după filtre, cele mai recente primele (după start).
   *
   * @param page numărul paginii, de la 0
   * @param size dimensiunea paginii, între 1 și {@value #MAX_PAGE_SIZE}
   */
  @Transactional(readOnly = true)
  public PageResponse<ReservationResponse> findMine(
      long userId, ReservationFilter filter, int page, int size) {
    PageRequest pageRequest =
        PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "start").and(Sort.by(Sort.Direction.DESC, "id")));
    return PageResponse.from(
        reservationRepository.findAll(
            ReservationSpecifications.requestedBy(userId, filter), pageRequest),
        ReservationResponse::from);
  }

  /**
   * Detaliul unei rezervări, cu istoricul aprobărilor. O pot vedea solicitantul, aprobatorii ei
   * (Coordonatorul grantului, Responsabilul laboratorului resursei sau cine a decis deja asupra ei)
   * și Administratorul de sistem.
   *
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}; 403 {@code REZERVARE_INACCESIBILA}
   */
  @Transactional(readOnly = true)
  public ReservationDetailResponse findById(long reservationId, AuthenticatedUser user) {
    Reservation reservation = getReservation(reservationId);
    if (!canView(reservation, user)) {
      throw ApiException.forbidden(
          "REZERVARE_INACCESIBILA", "Nu ai acces la rezervarea cu id-ul " + reservationId + ".");
    }
    return toDetail(reservation);
  }

  /**
   * Cererile care așteaptă o decizie: cele {@code InAsteptareCoordonator} de pe granturile date și
   * cele {@code InAsteptareLaborator} din laboratoarele date, cele mai apropiate primele (după
   * start). Cererile al căror start a trecut nu mai apar: nu mai pot fi aprobate.
   *
   * @param grantIds granturile coordonate de aprobator (nivelul 1)
   * @param laboratoryIds laboratoarele de care răspunde aprobatorul (nivelul 2)
   */
  @Transactional(readOnly = true)
  public List<ReservationDetailResponse> findAwaitingDecision(
      Collection<Long> grantIds, Collection<Long> laboratoryIds) {
    if (grantIds.isEmpty() && laboratoryIds.isEmpty()) {
      return List.of();
    }
    return toDetails(
        reservationRepository.findAll(
            ReservationSpecifications.awaitingDecision(grantIds, laboratoryIds, clock.instant()),
            Sort.by("start").and(Sort.by("id"))));
  }

  /** Detaliile rezervărilor cu id-urile date, în ordinea id-urilor primite. */
  @Transactional(readOnly = true)
  public List<ReservationDetailResponse> findDetails(List<Long> reservationIds) {
    Map<Long, Reservation> byId =
        reservationRepository.findWithDetailsByIdIn(reservationIds).stream()
            .collect(Collectors.toMap(Reservation::getId, Function.identity()));
    return toDetails(
        reservationIds.stream().distinct().map(byId::get).filter(Objects::nonNull).toList());
  }

  /**
   * Citește rezervarea și îi blochează rândul până la finalul tranzacției apelantului, ca deciziile
   * (aprobare, respingere, anulare) asupra aceleiași rezervări să nu se suprapună. Cere o
   * tranzacție deja pornită ({@code MANDATORY}): fără ea, blocarea s-ar elibera imediat.
   *
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Reservation getForUpdate(long reservationId) {
    return reservationRepository
        .findByIdForUpdate(reservationId)
        .orElseThrow(() -> notFound(reservationId));
  }

  /**
   * Creează o cerere de rezervare în starea {@code InAsteptareCoordonator}, cu hard lock (TO-5).
   *
   * <p>Grantul și resursa se blochează (în această ordine, ca să nu apară deadlock-uri), apoi se
   * verifică starea resursei, suprapunerea și soldul, iar cererea se salvează în aceeași
   * tranzacție. Cererile simultane pe aceeași resursă sau pe același grant se serializează, deci a
   * doua vede rezervarea primei și primește 409 cu intervalele libere. Constrângerea {@code
   * rezervare_fara_suprapunere} rămâne ultima linie de apărare.
   *
   * @param userId solicitantul; dreptul de a rezerva pe grant se verifică în controller
   * @throws ApiException 400 {@code INTERVAL_INVALID}; 404 {@code GRANT_INEXISTENT} sau {@code
   *     RESURSA_INEXISTENTA}; 409 {@code INTERVAL_OCUPAT}; 422 {@code RESURSA_INDISPONIBILA} sau
   *     {@code SOLD_INSUFICIENT}
   */
  @Transactional
  public ReservationResponse create(CreateReservationRequest request, long userId) {
    Instant start = request.start().toInstant();
    Instant end = request.sfarsit().toInstant();
    BigDecimal hours = requestedHours(start, end);

    GrantResponse grant = grantService.getBalanceForUpdate(request.grantId());
    Resource resource = resourceService.getForUpdate(request.resursaId());
    if (resource.getStatus() != ResourceStatus.ACTIVE) {
      throw ApiException.businessRule(
          "RESURSA_INDISPONIBILA",
          "Resursa este în starea "
              + resource.getStatus().getValue()
              + " și nu poate fi rezervată.");
    }
    Duration preparation = Duration.ofMinutes(resource.getPreparationTimeMinutes());
    if (!reservationRepository
        .findBlocking(resource.getId(), ReservationStatus.BLOCKING, start, end.plus(preparation))
        .isEmpty()) {
      throw intervalOccupied(freeIntervals(resource.getId(), preparation, request.start()));
    }
    if (grant.oreRamase().compareTo(hours) < 0) {
      throw ApiException.businessRule(
          "SOLD_INSUFICIENT",
          "Grantul mai are "
              + formatHours(grant.oreRamase())
              + " ore disponibile, iar rezervarea necesită "
              + formatHours(hours)
              + " ore.");
    }

    Reservation reservation =
        new Reservation(
            userService.getUser(userId),
            resource,
            grant.id(),
            start,
            end,
            hours,
            request.scop().strip());
    try {
      reservation = reservationRepository.saveAndFlush(reservation);
    } catch (DataIntegrityViolationException ex) {
      if (!isOverlapViolation(ex)) {
        throw ex;
      }
      // Tranzacția este compromisă după eroare, deci intervalele libere nu se mai pot citi.
      log.warn(
          "Rezervare respinsă de constrângerea {} pe resursa {}",
          OVERLAP_CONSTRAINT,
          resource.getId());
      throw intervalOccupied(null);
    }
    log.info(
        "Rezervarea {} a fost creată de utilizatorul {} pe resursa {} (grantul {}, {} ore)",
        reservation.getId(),
        userId,
        resource.getId(),
        grant.id(),
        hours);
    // TODO: TO-9 - notificarea Coordonatorului grantului despre cererea nouă.
    return ReservationResponse.from(reservation);
  }

  /**
   * Anulează rezervarea la cererea solicitantului. Este permis doar din stările {@code
   * InAsteptareCoordonator}, {@code InAsteptareLaborator} și {@code Aprobata}, înainte de start.
   *
   * <p>Starea {@code Anulata} scoate rezervarea din constrângerea {@code
   * rezervare_fara_suprapunere} și din soldul grantului ({@code v_sold_grant}), deci intervalul și
   * orele se eliberează în aceeași tranzacție.
   *
   * @param reason motivul dat de solicitant, opțional; se trimite în notificare (TO-9)
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}; 403 {@code REZERVARE_ALTUI_UTILIZATOR};
   *     409 {@code ANULARE_NEPERMISA} sau {@code REZERVARE_INCEPUTA} (conflict cu starea curentă)
   */
  @Transactional
  public ReservationResponse cancel(long reservationId, long userId, String reason) {
    Reservation reservation = getForUpdate(reservationId);
    if (!reservation.isRequestedBy(userId)) {
      throw ApiException.forbidden(
          "REZERVARE_ALTUI_UTILIZATOR", "Poți anula doar rezervările tale.");
    }
    if (!ReservationStatus.CANCELLABLE.contains(reservation.getStatus())) {
      throw ApiException.conflict(
          "ANULARE_NEPERMISA",
          "Rezervarea în starea "
              + reservation.getStatus().getValue()
              + " nu mai poate fi anulată.");
    }
    if (!clock.instant().isBefore(reservation.getStart())) {
      throw ApiException.conflict(
          "REZERVARE_INCEPUTA", "Rezervarea a început deja și nu mai poate fi anulată.");
    }
    reservation.setStatus(ReservationStatus.CANCELLED);
    // Motivul este text liber, deci nu ajunge în log; îl va primi notificarea.
    log.info("Rezervarea {} a fost anulată de solicitantul {}", reservationId, userId);
    // TODO: TO-9 - notificarea aprobatorilor care aveau cererea în lucru, cu motivul anulării.
    return ReservationResponse.from(reservation);
  }

  private boolean canView(Reservation reservation, AuthenticatedUser user) {
    return reservation.isRequestedBy(user.userId())
        || user.isSystemAdmin()
        || user.coordinatesGrant(reservation.getGrantId())
        || user.managesLaboratory(reservation.getResource().getLaboratory().getId())
        // Cine a decis deja rămâne aprobator al cererii, chiar dacă între timp și-a schimbat rolul.
        || approvalService.hasDecided(reservation.getId(), user.userId());
  }

  private ReservationDetailResponse toDetail(Reservation reservation) {
    return ReservationDetailResponse.from(
        reservation, approvalService.findByReservation(reservation.getId()));
  }

  /** Detaliile unei liste: aprobările tuturor rezervărilor se citesc într-o singură interogare. */
  private List<ReservationDetailResponse> toDetails(List<Reservation> reservations) {
    Map<Long, List<ApprovalResponse>> approvals =
        approvalService.findByReservations(reservations.stream().map(Reservation::getId).toList());
    return reservations.stream()
        .map(reservation ->
            ReservationDetailResponse.from(
                reservation, approvals.getOrDefault(reservation.getId(), List.of())))
        .toList();
  }

  /**
   * Orele solicitate: durata intervalului, fără timpul de pregătire. Se rotunjesc în sus la 2
   * zecimale, ca orice interval valid să consume cel puțin 0,01 ore ({@code ore_solicitate > 0}).
   */
  private BigDecimal requestedHours(Instant start, Instant end) {
    if (!end.isAfter(start)) {
      throw ApiException.badRequest(
          "INTERVAL_INVALID", "Sfârșitul rezervării trebuie să fie după start.");
    }
    if (!start.isAfter(clock.instant())) {
      throw ApiException.badRequest(
          "INTERVAL_INVALID", "Startul rezervării trebuie să fie în viitor.");
    }
    BigDecimal hours =
        BigDecimal.valueOf(Duration.between(start, end).toMillis())
            .divide(MILLIS_PER_HOUR, 2, RoundingMode.UP);
    if (hours.compareTo(MAX_REQUESTED_HOURS) > 0) {
      throw ApiException.badRequest(
          "INTERVAL_INVALID", "O rezervare poate dura cel mult 999,99 ore.");
    }
    return hours;
  }

  /**
   * Intervalele libere ale resursei în ziua startului cerut (după fusul orar al cererii), din
   * momentul curent încolo. Un interval liber se termină cu timpul de pregătire înaintea rezervării
   * următoare, ca o rezervare care îl ocupă complet să nu o atingă.
   */
  private List<ApiError.FreeInterval> freeIntervals(
      long resourceId, Duration preparation, OffsetDateTime requestedStart) {
    OffsetDateTime day = requestedStart.truncatedTo(ChronoUnit.DAYS);
    Instant dayStart = day.toInstant();
    Instant dayEnd = day.plusDays(1).toInstant();
    // Și o rezervare care începe imediat după miezul nopții scurtează ultimul interval al zilei.
    List<Reservation> blocking =
        reservationRepository.findBlocking(
            resourceId, ReservationStatus.BLOCKING, dayStart, dayEnd.plus(preparation));

    List<ApiError.FreeInterval> free = new ArrayList<>();
    Instant cursor = latest(dayStart, clock.instant());
    for (Reservation other : blocking) {
      Instant gapEnd = earliest(other.getStart().minus(preparation), dayEnd);
      if (gapEnd.isAfter(cursor)) {
        free.add(new ApiError.FreeInterval(cursor, gapEnd));
      }
      cursor = latest(cursor, other.getBlockedEnd());
    }
    if (dayEnd.isAfter(cursor)) {
      free.add(new ApiError.FreeInterval(cursor, dayEnd));
    }
    return free;
  }

  private static ApiException intervalOccupied(List<ApiError.FreeInterval> freeIntervals) {
    return ApiException.intervalConflict(
        "INTERVAL_OCUPAT",
        "Intervalul selectat se suprapune cu o altă rezervare a resursei"
            + " (inclusiv timpul de pregătire).",
        freeIntervals);
  }

  /** Încălcarea constrângerii de excludere {@code rezervare_fara_suprapunere} (SQLSTATE 23P01). */
  private static boolean isOverlapViolation(DataIntegrityViolationException ex) {
    return ex.getMostSpecificCause() instanceof SQLException sql
        && EXCLUSION_VIOLATION.equals(sql.getSQLState())
        && String.valueOf(sql.getMessage()).contains(OVERLAP_CONSTRAINT);
  }

  /** Orele pentru mesajele către utilizator, cu virgulă zecimală: {@code 1.50} → „1,5”. */
  private static String formatHours(BigDecimal hours) {
    return hours.stripTrailingZeros().toPlainString().replace('.', ',');
  }

  private static Instant latest(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }

  private static Instant earliest(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  private Reservation getReservation(long id) {
    return reservationRepository.findById(id).orElseThrow(() -> notFound(id));
  }

  private static ApiException notFound(long id) {
    return ApiException.notFound(
        "REZERVARE_INEXISTENTA", "Rezervarea cu id-ul " + id + " nu există.");
  }
}
