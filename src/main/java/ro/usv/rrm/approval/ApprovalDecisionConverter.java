package ro.usv.rrm.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Salvează {@link ApprovalDecision} cu valorile din schema bazei de date. */
@Converter(autoApply = true)
public class ApprovalDecisionConverter implements AttributeConverter<ApprovalDecision, String> {

  @Override
  public String convertToDatabaseColumn(ApprovalDecision decision) {
    return decision == null ? null : decision.getValue();
  }

  @Override
  public ApprovalDecision convertToEntityAttribute(String value) {
    return value == null ? null : ApprovalDecision.fromValue(value);
  }
}
