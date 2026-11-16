package ro.usv.rrm.grant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrantRepository extends JpaRepository<Grant, Long> {

  /** Grantul cu soldul de ore; alias-urile sunt între ghilimele ca PostgreSQL să nu le schimbe. */
  String WITH_BALANCE =
      """
      SELECT g.id AS "id", g.cod AS "code", g.coordonator_id AS "coordinatorId",
             g.ore_alocate AS "allocatedHours", s.ore_consumate AS "consumedHours",
             s.ore_ramase AS "remainingHours", g.data_start AS "startDate",
             g.data_sfarsit AS "endDate"
      FROM grant_cercetare g JOIN v_sold_grant s ON s.id = g.id
      """;

  /**
   * Condiția „utilizatorul face parte din grant”: Coordonator al grantului sau membru.
   * Coordonatorul se ia din rolul cu scop din {@code utilizator_rol}, aceeași sursă ca restul
   * verificărilor RBAC ({@code AuthenticatedUser.coordinatesGrant}), nu din {@code
   * grant_cercetare.coordonator_id}.
   */
  String PART_OF_GRANT =
      """
      (EXISTS (SELECT 1 FROM utilizator_rol r
               WHERE r.utilizator_id = :userId AND r.rol = 'CoordonatorGrant'
                 AND r.tip_scop = 'Grant' AND r.scop_id = g.id)
       OR EXISTS (SELECT 1 FROM membru_grant m WHERE m.grant_id = g.id AND m.utilizator_id = :userId))
      """;

  @Query(value = WITH_BALANCE + " ORDER BY g.cod", nativeQuery = true)
  List<GrantBalance> findAllWithBalance();

  @Query(value = WITH_BALANCE + " WHERE " + PART_OF_GRANT + " ORDER BY g.cod", nativeQuery = true)
  List<GrantBalance> findAllWithBalanceForUser(@Param("userId") long userId);

  @Query(value = WITH_BALANCE + " WHERE g.id = :grantId", nativeQuery = true)
  Optional<GrantBalance> findWithBalanceById(@Param("grantId") long grantId);

  /**
   * Blochează rândul grantului până la finalul tranzacției și întoarce id-ul, dacă grantul există.
   * {@code NO KEY UPDATE} nu blochează cheile străine spre grant (ex. inserarea unei rezervări),
   * doar alte blocări ale aceluiași grant.
   */
  @Query(
      value = "SELECT id FROM grant_cercetare WHERE id = :grantId FOR NO KEY UPDATE",
      nativeQuery = true)
  Optional<Long> lockById(@Param("grantId") long grantId);

  /**
   * {@code true} dacă utilizatorul este membru al grantului sau dacă grantul nu există (ca
   * verificarea drepturilor să lase Service-ul să răspundă cu 404). O singură interogare.
   */
  @Query(
      value =
          """
          SELECT NOT EXISTS (SELECT 1 FROM grant_cercetare WHERE id = :grantId)
              OR EXISTS (SELECT 1 FROM membru_grant
                         WHERE grant_id = :grantId AND utilizator_id = :userId)
          """,
      nativeQuery = true)
  boolean isMemberOrMissing(@Param("grantId") long grantId, @Param("userId") long userId);
}
