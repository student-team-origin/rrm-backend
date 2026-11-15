package ro.usv.rrm.approval;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.auth.AuthenticatedUser;
import ro.usv.rrm.auth.ScopedRole;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationService;
import ro.usv.rrm.reservation.ReservationStatus;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.UserService;

/**
 * Fluxul de aprobare pe 2 niveluri: Coordonatorul grantului ({@code InAsteptareCoordonator} →
 * {@code InAsteptareLaborator}), apoi Responsabilul laboratorului ({@code InAsteptareLaborator} →
 * {@code Aprobata}). Oricare dintre ei poate respinge cererea la nivelul lui ({@code Respinsa}).
 *
 * <p>Administratorul de sistem nu aprobă: fluxul are doar cele 2 niveluri din PRD.
 *
 * <p>Separat de {@link ApprovalService}, pe care îl folosește {@link ReservationService}: acest
 * serviciu depinde de {@link ReservationService}, iar invers ar fi o dependență circulară.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalFlowService {

  private final ApprovalRepository approvalRepository;
  private final ReservationService reservationService;
  private final UserService userService;
  private final Clock clock;

  /**
   * Cererile pentru aprobatorul dat: cele care îi așteaptă decizia ({@code inAsteptare}, doar cu
   * start în viitor) sau cele pe care le-a aprobat ori respins deja. Rolul de aprobator se verifică
   * în controller ({@code @PreAuthorize}).
   */
  @Transactional(readOnly = true)
  public List<ReservationDetailResponse> findForApprover(
      AuthenticatedUser user, ApprovalListStatus status) {
    return switch (status) {
      case PENDING ->
          reservationService.findAwaitingDecision(
              scopeIds(user, Role.GRANT_COORDINATOR, ScopeType.GRANT),
              scopeIds(user, Role.LAB_MANAGER, ScopeType.LABORATORY));
      case APPROVED -> decidedBy(user, ApprovalDecision.APPROVED);
      case REJECTED -> decidedBy(user, ApprovalDecision.REJECTED);
    };
  }

  /**
   * Aprobă cererea la nivelul ei curent: nivelul 1 o trimite laboratorului, nivelul 2 o aprobă
   * definitiv.
   *
   * @param observation observația aprobatorului, opțională
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}; 403 {@code FARA_DREPT_APROBARE} sau
   *     {@code AUTO_APROBARE_INTERZISA}; 409 {@code CERERE_FARA_DECIZIE_IN_ASTEPTARE} sau {@code
   *     REZERVARE_INCEPUTA}
   */
  @Transactional
  public ApprovalResponse approve(long reservationId, AuthenticatedUser user, String observation) {
    Reservation reservation = reservationService.getForUpdate(reservationId);
    int level = checkCanDecide(reservation, user);
    ReservationStatus next =
        level == Approval.COORDINATOR_LEVEL
            ? ReservationStatus.PENDING_LABORATORY
            : ReservationStatus.APPROVED;
    return decide(reservation, user, level, ApprovalDecision.APPROVED, observation, next);
  }

  /**
   * Respinge cererea la nivelul ei curent. Starea {@code Respinsa} scoate rezervarea din
   * constrângerea {@code rezervare_fara_suprapunere} și din soldul grantului, deci intervalul și
   * orele se eliberează.
   *
   * @param reason motivul respingerii, obligatoriu (CHECK în baza de date)
   * @throws ApiException aceleași ca la {@link #approve}
   */
  @Transactional
  public ApprovalResponse reject(long reservationId, AuthenticatedUser user, String reason) {
    Reservation reservation = reservationService.getForUpdate(reservationId);
    int level = checkCanDecide(reservation, user);
    return decide(
        reservation, user, level, ApprovalDecision.REJECTED, reason, ReservationStatus.REJECTED);
  }

  /**
   * Verifică, în ordine: dacă utilizatorul are vreun drept asupra cererii (ca un străin să nu afle
   * starea ei), dacă nu e propria lui cerere, dacă cererea mai așteaptă o decizie, dacă nivelul
   * curent este al lui și dacă cererea nu a început deja.
   *
   * @return nivelul la care decide utilizatorul
   */
  private int checkCanDecide(Reservation reservation, AuthenticatedUser user) {
    boolean isCoordinator = user.coordinatesGrant(reservation.getGrantId());
    boolean isLaboratoryManager =
        user.managesLaboratory(reservation.getResource().getLaboratory().getId());
    if (!isCoordinator && !isLaboratoryManager) {
      throw noRight();
    }
    if (reservation.isRequestedBy(user.userId())) {
      throw ApiException.forbidden(
          "AUTO_APROBARE_INTERZISA", "Nu poți decide asupra propriei cereri de rezervare.");
    }
    int level =
        switch (reservation.getStatus()) {
          case PENDING_COORDINATOR -> Approval.COORDINATOR_LEVEL;
          case PENDING_LABORATORY -> Approval.LABORATORY_LEVEL;
          default ->
              throw ApiException.conflict(
                  "CERERE_FARA_DECIZIE_IN_ASTEPTARE",
                  "Rezervarea în starea "
                      + reservation.getStatus().getValue()
                      + " nu mai așteaptă o decizie.");
        };
    boolean isLevelApprover =
        level == Approval.COORDINATOR_LEVEL ? isCoordinator : isLaboratoryManager;
    if (!isLevelApprover) {
      throw noRight();
    }
    if (!clock.instant().isBefore(reservation.getStart())) {
      throw ApiException.conflict(
          "REZERVARE_INCEPUTA", "Rezervarea a început deja; nu se mai poate decide asupra ei.");
    }
    return level;
  }

  private ApprovalResponse decide(
      Reservation reservation,
      AuthenticatedUser user,
      int level,
      ApprovalDecision decision,
      String observation,
      ReservationStatus next) {
    Approval approval =
        new Approval(reservation, level, userService.getUser(user.userId()), decision);
    approval.setObservation(observation);
    Approval saved = approvalRepository.saveAndFlush(approval);
    reservation.setStatus(next);
    log.info(
        "Rezervarea {}: decizia {} la nivelul {} (utilizatorul {}), starea nouă {}",
        reservation.getId(),
        decision.getValue(),
        level,
        user.userId(),
        next.getValue());
    // TODO: TO-9 - notificarea solicitantului și, după nivelul 1, a responsabilului de laborator.
    return ApprovalResponse.from(saved);
  }

  private List<ReservationDetailResponse> decidedBy(
      AuthenticatedUser user, ApprovalDecision decision) {
    return reservationService.findDetails(
        approvalRepository.findReservationIdsDecidedBy(user.userId(), decision));
  }

  /** Id-urile granturilor sau laboratoarelor în care utilizatorul are rolul dat. */
  private static Set<Long> scopeIds(AuthenticatedUser user, Role role, ScopeType scopeType) {
    return user.roles().stream()
        .filter(item -> item.role() == role && item.scopeType() == scopeType)
        .map(ScopedRole::scopeId)
        .collect(Collectors.toSet());
  }

  private static ApiException noRight() {
    return ApiException.forbidden(
        "FARA_DREPT_APROBARE", "Nu poți decide asupra acestei rezervări la nivelul ei curent.");
  }
}
