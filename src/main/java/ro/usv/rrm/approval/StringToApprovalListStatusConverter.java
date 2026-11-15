package ro.usv.rrm.approval;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Citește parametrul {@code stare} din {@code GET /approvals} cu valorile din contract ({@code
 * inAsteptare}, {@code aprobate}, {@code respinse}). O valoare necunoscută produce 400.
 */
@Component
class StringToApprovalListStatusConverter implements Converter<String, ApprovalListStatus> {

  @Override
  public ApprovalListStatus convert(String value) {
    return ApprovalListStatus.fromValue(value);
  }
}
