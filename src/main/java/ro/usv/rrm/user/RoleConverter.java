package ro.usv.rrm.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Salvează {@link Role} cu valorile din schema bazei de date. */
@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

  @Override
  public String convertToDatabaseColumn(Role item) {
    return item == null ? null : item.getValue();
  }

  @Override
  public Role convertToEntityAttribute(String value) {
    return value == null ? null : Role.fromValue(value);
  }
}
