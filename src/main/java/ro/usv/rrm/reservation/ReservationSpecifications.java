package ro.usv.rrm.reservation;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Criteriile pentru „Rezervările mele”, construite din {@link ReservationFilter}. */
final class ReservationSpecifications {

  private ReservationSpecifications() {}

  /**
   * Rezervările cerute de utilizatorul dat. {@code from} și {@code to} păstrează rezervările al
   * căror interval {@code [start, sfarsit)} se intersectează cu intervalul cerut.
   */
  static Specification<Reservation> requestedBy(long requesterId, ReservationFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("requester").get("id"), requesterId));
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.from() != null) {
        predicates.add(cb.greaterThan(root.get("end"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(cb.lessThan(root.get("start"), filter.to()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
