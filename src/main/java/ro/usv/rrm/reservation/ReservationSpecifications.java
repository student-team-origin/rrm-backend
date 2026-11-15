package ro.usv.rrm.reservation;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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

  /**
   * Cererile care așteaptă decizia unui aprobator: nivelul 1 pe granturile date, nivelul 2 în
   * laboratoarele date, doar cele care încep după {@code now}. O colecție goală nu adaugă nicio
   * condiție.
   */
  static Specification<Reservation> awaitingDecision(
      Collection<Long> grantIds, Collection<Long> laboratoryIds, Instant now) {
    return (root, query, cb) -> {
      List<Predicate> alternatives = new ArrayList<>();
      if (!grantIds.isEmpty()) {
        alternatives.add(
            cb.and(
                cb.equal(root.get("status"), ReservationStatus.PENDING_COORDINATOR),
                root.get("grantId").in(grantIds)));
      }
      if (!laboratoryIds.isEmpty()) {
        alternatives.add(
            cb.and(
                cb.equal(root.get("status"), ReservationStatus.PENDING_LABORATORY),
                root.get("resource").get("laboratory").get("id").in(laboratoryIds)));
      }
      return alternatives.isEmpty()
          ? cb.disjunction()
          : cb.and(
              cb.greaterThan(root.get("start"), now),
              cb.or(alternatives.toArray(Predicate[]::new)));
    };
  }
}
