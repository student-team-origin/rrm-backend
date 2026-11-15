package ro.usv.rrm.grant;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un grant cu soldul de ore din view-ul {@code v_sold_grant} (proiecție pentru interogări). */
public interface GrantBalance {

  Long getId();

  String getCode();

  Long getCoordinatorId();

  BigDecimal getAllocatedHours();

  BigDecimal getConsumedHours();

  BigDecimal getRemainingHours();

  LocalDate getStartDate();

  LocalDate getEndDate();
}
