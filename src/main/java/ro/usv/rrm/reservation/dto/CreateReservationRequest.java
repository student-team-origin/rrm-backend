package ro.usv.rrm.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Cererea de rezervare (schema {@code RezervareCreate} din api-contract/openapi.yaml).
 *
 * <p>Startul și sfârșitul păstrează fusul orar trimis de client (Jackson le-ar converti implicit în
 * UTC): după el se alege ziua pentru care se propun intervalele libere, dacă intervalul cerut este
 * ocupat.
 *
 * @param resursaId id-ul resursei (obligatoriu)
 * @param grantId id-ul grantului pe ale cărui ore se rezervă (obligatoriu)
 * @param start începutul intervalului (obligatoriu, în viitor)
 * @param sfarsit sfârșitul intervalului (obligatoriu, după start)
 * @param scop scopul rezervării (obligatoriu, maximum 1000 de caractere)
 */
public record CreateReservationRequest(
    @NotNull(message = "Resursa este obligatorie.") Long resursaId,
    @NotNull(message = "Grantul este obligatoriu.") Long grantId,
    @NotNull(message = "Startul este obligatoriu.")
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime start,
    @NotNull(message = "Sfârșitul este obligatoriu.")
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime sfarsit,
    @NotBlank(message = "Scopul este obligatoriu.")
        @Size(max = 1000, message = "Scopul poate avea cel mult 1000 de caractere.")
        String scop) {}
