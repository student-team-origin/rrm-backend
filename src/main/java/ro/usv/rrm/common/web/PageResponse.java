package ro.usv.rrm.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Răspuns paginat: metadatele {@code PageMeta} din docs/openapi.yaml + lista de elemente.
 *
 * @param <T> tipul elementelor (un DTO de răspuns)
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

  /** Construiește răspunsul dintr-o pagină JPA, convertind fiecare entitate în DTO. */
  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());
  }
}
