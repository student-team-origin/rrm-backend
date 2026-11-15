package ro.usv.rrm.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Salvează {@link ScopeType} cu valorile din schema bazei de date. */
@Converter(autoApply = true)
public class ScopeTypeConverter implements AttributeConverter<ScopeType, String> {

  @Override
  public String convertToDatabaseColumn(ScopeType item) {
    return item == null ? null : item.getValue();
  }

  @Override
  public ScopeType convertToEntityAttribute(String value) {
    return value == null ? null : ScopeType.fromValue(value);
  }
}
