package ro.usv.rrm.approval.dto;

import java.time.Instant;
import ro.usv.rrm.approval.Approval;
import ro.usv.rrm.approval.ApprovalDecision;

/**
 * O decizie din istoricul aprobărilor (schema {@code Aprobare} din api-contract/openapi.yaml).
 *
 * @param id id-ul aprobării
 * @param rezervareId id-ul rezervării
 * @param aprobatorId id-ul utilizatorului care a decis
 * @param nivel 1 = Coordonator Grant, 2 = Responsabil Laborator
 * @param decizie {@code Aprobat} sau {@code Respins}
 * @param observatie observația aprobatorului (obligatorie la respingere)
 * @param dataDecizie momentul deciziei
 */
public record ApprovalResponse(
    Long id,
    Long rezervareId,
    Long aprobatorId,
    int nivel,
    ApprovalDecision decizie,
    String observatie,
    Instant dataDecizie) {

  public static ApprovalResponse from(Approval approval) {
    return new ApprovalResponse(
        approval.getId(),
        approval.getReservation().getId(),
        approval.getApprover().getId(),
        approval.getLevel(),
        approval.getDecision(),
        approval.getObservation(),
        approval.getDecidedAt());
  }
}
