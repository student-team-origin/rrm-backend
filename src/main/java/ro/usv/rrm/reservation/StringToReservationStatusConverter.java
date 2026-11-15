package ro.usv.rrm.reservation;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Citește parametrul {@code stare} din URL (ex. {@code ?stare=Aprobata}) cu valorile din contract,
 * nu cu numele constantelor Java. O valoare necunoscută produce 400.
 */
@Component
class StringToReservationStatusConverter implements Converter<String, ReservationStatus> {

  @Override
  public ReservationStatus convert(String value) {
    return ReservationStatus.fromValue(value);
  }
}
