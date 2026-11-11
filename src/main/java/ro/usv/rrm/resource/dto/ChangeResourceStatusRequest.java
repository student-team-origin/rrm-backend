package ro.usv.rrm.resource.dto;

import jakarta.validation.constraints.NotNull;
import ro.usv.rrm.resource.ResourceStatus;

/**
 * Cererea de schimbare a stării unei resurse ({@code PATCH /resources/{id}/status}).
 *
 * @param stare noua stare (obligatorie)
 * @param motiv motivul schimbării (opțional), de exemplu „Calibrare anuală”
 */
public record ChangeResourceStatusRequest(
    @NotNull(message = "Starea este obligatorie.") ResourceStatus stare, String motiv) {}
