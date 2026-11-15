package ro.usv.rrm.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cererea de respingere (schema {@code DecizieRequest} cu {@code observatie} obligatorie, din
 * api-contract/openapi.yaml).
 *
 * @param observatie motivul respingerii, obligatoriu
 */
public record RejectionRequest(
    @NotBlank(message = "Motivul respingerii este obligatoriu.")
        @Size(max = 1000, message = "Motivul poate avea cel mult 1000 de caractere.")
        String observatie) {}
