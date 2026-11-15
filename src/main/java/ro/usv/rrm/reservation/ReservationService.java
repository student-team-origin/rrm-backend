package ro.usv.rrm.reservation;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.ApprovalService;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;

/** Rezervările utilizatorului: lista „Rezervările mele”, detaliul și anularea. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

  static final int MAX_PAGE_SIZE = 100;

  private final ReservationRepository reservationRepository;
  private final ApprovalService approvalService;
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

  private Reservation getReservation(long id) {
    return reservationRepository.findById(id).orElseThrow(() -> notFound(id));
  }

  private static ApiException notFound(long id) {
    return ApiException.notFound(
        "REZERVARE_INEXISTENTA", "Rezervarea cu id-ul " + id + " nu există.");
  }
}
