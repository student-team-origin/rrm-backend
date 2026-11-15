package ro.usv.rrm.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * Observația opțională la aprobare (schema {@code DecizieRequest} din api-contract/openapi.yaml).
 *
 * @param observatie observația aprobatorului, opțională
 */
public record DecisionRequest(
    @Size(max = 1000, message = "Observația poate avea cel mult 1000 de caractere.")
        String observatie) {}
