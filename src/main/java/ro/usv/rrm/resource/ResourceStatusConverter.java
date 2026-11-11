package ro.usv.rrm.resource;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Salvează {@link ResourceStatus} în coloana {@code stare} cu valorile din schema bazei de date.
 */
@Converter(autoApply = true)
public class ResourceStatusConverter implements AttributeConverter<ResourceStatus, String> {

  @Override
  public String convertToDatabaseColumn(ResourceStatus status) {
    return status == null ? null : status.getValue();
  }

  @Override
  public ResourceStatus convertToEntityAttribute(String value) {
    return value == null ? null : ResourceStatus.fromValue(value);
  }
}
