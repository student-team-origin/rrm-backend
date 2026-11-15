package ro.usv.rrm.auth;

import java.util.UUID;

/**
 * Utilizatorul autentificat al cererii curente, pus în {@code SecurityContext} de {@link
 * JwtAuthenticationFilter}. În controllere se primește cu {@code @AuthenticationPrincipal}.
 *
 * @param userId id-ul utilizatorului
 * @param sessionTokenId {@code jti}-ul sesiunii din tabela {@code sesiune}
 */
public record AuthenticatedUser(long userId, UUID sessionTokenId) {}
