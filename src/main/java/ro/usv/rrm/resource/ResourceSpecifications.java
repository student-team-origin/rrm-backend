package ro.usv.rrm.resource;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Criteriile de căutare în catalogul de resurse, construite din {@link ResourceFilter}. */
final class ResourceSpecifications {

  private static final char LIKE_ESCAPE = '\\';

  private ResourceSpecifications() {}

  static Specification<Resource> matching(ResourceFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (filter.q() != null && !filter.q().isBlank()) {
        String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("specifications")), pattern, LIKE_ESCAPE)));
      }
      if (filter.categoryId() != null) {
        predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
      }
      if (filter.laboratoryId() != null) {
        predicates.add(cb.equal(root.get("laboratory").get("id"), filter.laboratoryId()));
      }
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Caracterele {@code %} și {@code _} din textul căutat se tratează literal, nu ca wildcard. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
