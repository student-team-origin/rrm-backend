package ro.usv.rrm.user;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Citește parametrul {@code rol} din URL (ex. {@code ?rol=CoordonatorGrant}) cu valorile din
 * contract, nu cu numele constantelor Java. O valoare necunoscută produce 400.
 */
@Component
class StringToRoleConverter implements Converter<String, Role> {

  @Override
  public Role convert(String value) {
    return Role.fromValue(value);
  }
}
