package ro.usv.rrm.reservation;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.ApprovalService;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.common.web.PageResponse;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.reservation.dto.ReservationResponse;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.UserService;
import ro.usv.rrm.user.dto.UserRoleResponse;

/** Rezervările utilizatorului: lista „Rezervările mele”, detaliul și anularea. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

  static final int MAX_PAGE_SIZE = 100;

  private final ReservationRepository reservationRepository;
  private final ApprovalService approvalService;
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
  public ReservationDetailResponse findById(long reservationId, long userId) {
    Reservation reservation = getReservation(reservationId);
    if (!canView(reservation, userId)) {
      throw ApiException.forbidden(
          "REZERVARE_INACCESIBILA", "Nu ai acces la rezervarea cu id-ul " + reservationId + ".");
    }
    return ReservationDetailResponse.from(
        reservation, approvalService.findByReservation(reservationId));
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
    Reservation reservation =
        reservationRepository
            .findByIdForUpdate(reservationId)
            .orElseThrow(() -> notFound(reservationId));
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

  private boolean canView(Reservation reservation, long userId) {
    if (reservation.isRequestedBy(userId)) {
      return true;
    }
    // TODO: TO-33 - verificarea rolurilor cu scop se mută în mecanismul comun de RBAC.
    List<UserRoleResponse> roles = userService.findById(userId).roluri();
    long laboratoryId = reservation.getResource().getLaboratory().getId();
    boolean isApprover =
        roles.stream()
            .anyMatch(role ->
                role.rol() == Role.SYSTEM_ADMIN
                    || hasScopedRole(
                        role, Role.GRANT_COORDINATOR, ScopeType.GRANT, reservation.getGrantId())
                    || hasScopedRole(role, Role.LAB_MANAGER, ScopeType.LABORATORY, laboratoryId));
    // Cine a decis deja rămâne aprobator al cererii, chiar dacă între timp și-a schimbat rolul.
    return isApprover || approvalService.hasDecided(reservation.getId(), userId);
  }

  private static boolean hasScopedRole(
      UserRoleResponse role, Role expectedRole, ScopeType scopeType, Long scopeId) {
    return role.rol() == expectedRole
        && role.tipScop() == scopeType
        && scopeId.equals(role.scopId());
  }

  private Reservation getReservation(long id) {
    return reservationRepository.findById(id).orElseThrow(() -> notFound(id));
  }

  private static ApiException notFound(long id) {
    return ApiException.notFound(
        "REZERVARE_INEXISTENTA", "Rezervarea cu id-ul " + id + " nu există.");
  }
}
