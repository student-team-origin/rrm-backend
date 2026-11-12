package ro.usv.rrm.resource;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Citește parametrul {@code stare} din URL (ex. {@code ?stare=Mentenanta}) cu valorile din
 * contract, nu cu numele constantelor Java. O valoare necunoscută produce 400.
 */
@Component
class StringToResourceStatusConverter implements Converter<String, ResourceStatus> {

  @Override
  public ResourceStatus convert(String value) {
    return ResourceStatus.fromValue(value);
  }
}
