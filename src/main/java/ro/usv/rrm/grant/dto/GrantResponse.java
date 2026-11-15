package ro.usv.rrm.grant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import ro.usv.rrm.grant.GrantBalance;

/**
 * Un grant, cu soldul de ore (schema {@code Grant} din api-contract/openapi.yaml).
 *
 * @param id id-ul grantului
 * @param cod codul grantului
 * @param coordonatorId id-ul Coordonatorului grantului
 * @param oreAlocate orele alocate pentru rezervări
 * @param oreConsumate orele rezervărilor în curs, aprobate sau finalizate
 * @param oreRamase orele care mai pot fi rezervate
 * @param dataStart prima zi a grantului
 * @param dataSfarsit ultima zi a grantului
 */
public record GrantResponse(
    Long id,
    String cod,
    Long coordonatorId,
    BigDecimal oreAlocate,
    BigDecimal oreConsumate,
    BigDecimal oreRamase,
    LocalDate dataStart,
    LocalDate dataSfarsit) {

  public static GrantResponse from(GrantBalance grant) {
    return new GrantResponse(
        grant.getId(),
        grant.getCode(),
        grant.getCoordinatorId(),
        grant.getAllocatedHours(),
        grant.getConsumedHours(),
        grant.getRemainingHours(),
        grant.getStartDate(),
        grant.getEndDate());
  }
}
