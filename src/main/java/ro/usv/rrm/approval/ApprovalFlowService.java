package ro.usv.rrm.approval;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.usv.rrm.approval.dto.ApprovalResponse;
import ro.usv.rrm.common.error.ApiException;
import ro.usv.rrm.reservation.Reservation;
import ro.usv.rrm.reservation.ReservationService;
import ro.usv.rrm.reservation.ReservationStatus;
import ro.usv.rrm.reservation.dto.ReservationDetailResponse;
import ro.usv.rrm.user.Role;
import ro.usv.rrm.user.ScopeType;
import ro.usv.rrm.user.UserService;
import ro.usv.rrm.user.dto.UserRoleResponse;

/**
 * Fluxul de aprobare pe 2 niveluri: Coordonatorul grantului ({@code InAsteptareCoordonator} →
 * {@code InAsteptareLaborator}), apoi Responsabilul laboratorului ({@code InAsteptareLaborator} →
 * {@code Aprobata}). Oricare dintre ei poate respinge cererea la nivelul lui ({@code Respinsa}).
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

  /**
   * Cererile pentru aprobatorul dat: cele care îi așteaptă decizia ({@code inAsteptare}) sau cele
   * pe care le-a aprobat ori respins deja.
   *
   * @throws ApiException 403 {@code FARA_ROL_APROBATOR} dacă utilizatorul nu este nici Coordonator
   *     de grant, nici Responsabil de laborator
   */
  @Transactional(readOnly = true)
  public List<ReservationDetailResponse> findForApprover(long userId, ApprovalListStatus status) {
    ApproverScopes scopes = scopesOf(userId);
    if (scopes.isEmpty()) {
      throw ApiException.forbidden(
          "FARA_ROL_APROBATOR",
          "Doar coordonatorii de grant și responsabilii de laborator aprobă rezervări.");
    }
    return switch (status) {
      case PENDING ->
          reservationService.findAwaitingDecision(scopes.grantIds(), scopes.laboratoryIds());
      case APPROVED -> decidedBy(userId, ApprovalDecision.APPROVED);
      case REJECTED -> decidedBy(userId, ApprovalDecision.REJECTED);
    };
  }

  /**
   * Aprobă cererea la nivelul ei curent: nivelul 1 o trimite laboratorului, nivelul 2 o aprobă
   * definitiv.
   *
   * @param observation observația aprobatorului, opțională
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}; 403 {@code FARA_DREPT_APROBARE} dacă
   *     utilizatorul nu este aprobatorul nivelului curent; 409 {@code
   *     CERERE_FARA_DECIZIE_IN_ASTEPTARE} dacă cererea nu mai așteaptă o decizie
   */
  @Transactional
  public ApprovalResponse approve(long reservationId, long userId, String observation) {
    Reservation reservation = reservationService.getForUpdate(reservationId);
    int level = checkCanDecide(reservation, userId);
    ReservationStatus next =
        level == Approval.COORDINATOR_LEVEL
            ? ReservationStatus.PENDING_LABORATORY
            : ReservationStatus.APPROVED;
    return decide(reservation, userId, level, ApprovalDecision.APPROVED, observation, next);
  }

  /**
   * Respinge cererea la nivelul ei curent. Starea {@code Respinsa} scoate rezervarea din
   * constrângerea {@code rezervare_fara_suprapunere} și din soldul grantului, deci intervalul și
   * orele se eliberează.
   *
   * @param reason motivul respingerii, obligatoriu (CHECK în baza de date)
   * @throws ApiException 404 {@code REZERVARE_INEXISTENTA}; 403 {@code FARA_DREPT_APROBARE}; 409
   *     {@code CERERE_FARA_DECIZIE_IN_ASTEPTARE}
   */
  @Transactional
  public ApprovalResponse reject(long reservationId, long userId, String reason) {
    Reservation reservation = reservationService.getForUpdate(reservationId);
    int level = checkCanDecide(reservation, userId);
    return decide(
        reservation, userId, level, ApprovalDecision.REJECTED, reason, ReservationStatus.REJECTED);
  }

  /**
   * Verifică ordinea: întâi dacă utilizatorul are vreun drept asupra cererii (ca un străin să nu
   * afle starea ei), apoi dacă cererea mai așteaptă o decizie, apoi dacă nivelul curent este al
   * lui.
   *
   * @return nivelul la care decide utilizatorul
   */
  private int checkCanDecide(Reservation reservation, long userId) {
    ApproverScopes scopes = scopesOf(userId);
    long laboratoryId = reservation.getResource().getLaboratory().getId();
    boolean isCoordinator = scopes.grantIds().contains(reservation.getGrantId());
    boolean isLaboratoryManager = scopes.laboratoryIds().contains(laboratoryId);
    if (!isCoordinator && !isLaboratoryManager) {
      throw noRight();
    }
    return switch (reservation.getStatus()) {
      case PENDING_COORDINATOR -> {
        if (!isCoordinator) {
          throw noRight();
        }
        yield Approval.COORDINATOR_LEVEL;
      }
      case PENDING_LABORATORY -> {
        if (!isLaboratoryManager) {
          throw noRight();
        }
        yield Approval.LABORATORY_LEVEL;
      }
      default ->
          throw ApiException.conflict(
              "CERERE_FARA_DECIZIE_IN_ASTEPTARE",
              "Rezervarea în starea "
                  + reservation.getStatus().getValue()
                  + " nu mai așteaptă o decizie.");
    };
  }

  private ApprovalResponse decide(
      Reservation reservation,
      long userId,
      int level,
      ApprovalDecision decision,
      String observation,
      ReservationStatus next) {
    Approval approval = new Approval(reservation, level, userService.getUser(userId), decision);
    approval.setObservation(observation);
    Approval saved = approvalRepository.saveAndFlush(approval);
    reservation.setStatus(next);
    log.info(
        "Rezervarea {}: decizia {} la nivelul {} (utilizatorul {}), starea nouă {}",
        reservation.getId(),
        decision.getValue(),
        level,
        userId,
        next.getValue());
    // TODO: TO-9 - notificarea solicitantului și, după nivelul 1, a responsabilului de laborator.
    return ApprovalResponse.from(saved);
  }

  private List<ReservationDetailResponse> decidedBy(long userId, ApprovalDecision decision) {
    return reservationService.findDetails(
        approvalRepository.findReservationIdsDecidedBy(userId, decision));
  }

  // TODO: TO-33 - rolurile cu scop vor veni din mecanismul comun de RBAC.
  private ApproverScopes scopesOf(long userId) {
    List<UserRoleResponse> roles = userService.findById(userId).roluri();
    return new ApproverScopes(
        scopeIds(roles, Role.GRANT_COORDINATOR, ScopeType.GRANT),
        scopeIds(roles, Role.LAB_MANAGER, ScopeType.LABORATORY));
  }

  private static Set<Long> scopeIds(List<UserRoleResponse> roles, Role role, ScopeType scopeType) {
    return roles.stream()
        .filter(item -> item.rol() == role && item.tipScop() == scopeType)
        .map(UserRoleResponse::scopId)
        .collect(Collectors.toSet());
  }

  private static ApiException noRight() {
    return ApiException.forbidden(
        "FARA_DREPT_APROBARE", "Nu poți decide asupra acestei rezervări la nivelul ei curent.");
  }

  /**
   * Granturile și laboratoarele în care utilizatorul este aprobator.
   *
   * @param grantIds granturile în care este Coordonator (nivelul 1)
   * @param laboratoryIds laboratoarele în care este Responsabil (nivelul 2)
   */
  private record ApproverScopes(Set<Long> grantIds, Set<Long> laboratoryIds) {

    boolean isEmpty() {
      return grantIds.isEmpty() && laboratoryIds.isEmpty();
    }
  }
}
