package ro.usv.rrm.reservation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Salvează {@link ReservationStatus} cu valorile din schema bazei de date. */
@Converter(autoApply = true)
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

  @Override
  public String convertToDatabaseColumn(ReservationStatus status) {
    return status == null ? null : status.getValue();
  }

  @Override
  public ReservationStatus convertToEntityAttribute(String value) {
    return value == null ? null : ReservationStatus.fromValue(value);
  }
}
