package ro.usv.rrm.resource.dto;

/**
 * Rezultatul schimbării stării unei resurse.
 *
 * @param resursa resursa actualizată
 * @param rezervariAnulate numărul rezervărilor anulate din cauza schimbării de stare
 */
public record ChangeResourceStatusResponse(ResourceResponse resursa, int rezervariAnulate) {}
