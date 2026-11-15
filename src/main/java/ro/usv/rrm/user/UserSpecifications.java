package ro.usv.rrm.user;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Criteriile de căutare în lista utilizatorilor ({@code GET /users}). */
final class UserSpecifications {

  private static final char LIKE_ESCAPE = '\\';

  private UserSpecifications() {}

  /**
   * Utilizatorii cu rolul dat (pe orice scop) și al căror nume sau e-mail conține textul dat.
   * Filtrele {@code null} sau goale nu restrâng rezultatul.
   */
  static Specification<User> matching(Role role, String text) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (role != null) {
        // Subinterogare, nu join: un utilizator cu mai multe roluri ar apărea de mai multe ori.
        Subquery<Long> withRole = query.subquery(Long.class);
        var userRole = withRole.from(UserRole.class);
        withRole.select(userRole.get("user").get("id")).where(cb.equal(userRole.get("role"), role));
        predicates.add(root.get("id").in(withRole));
      }
      if (text != null && !text.isBlank()) {
        String pattern = "%" + escapeLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(root.get("email"), pattern, LIKE_ESCAPE)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  /** Caracterele {@code %} și {@code _} din textul căutat se tratează literal, nu ca wildcard. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
