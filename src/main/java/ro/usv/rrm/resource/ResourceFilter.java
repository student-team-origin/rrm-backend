package ro.usv.rrm.resource;

/**
 * Filtrele opționale pentru catalogul de resurse ({@code GET /resources}). Un filtru {@code null}
 * nu restrânge rezultatul; filtrele date se combină cu „și”.
 *
 * @param q text căutat în denumire sau specificații, fără diferență între litere mari și mici
 * @param categoryId id-ul categoriei
 * @param laboratoryId id-ul laboratorului
 * @param status starea resursei
 */
public record ResourceFilter(String q, Long categoryId, Long laboratoryId, ResourceStatus status) {}
