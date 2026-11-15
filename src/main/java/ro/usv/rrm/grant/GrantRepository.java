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

  /** Condiția „utilizatorul face parte din grant”: coordonator sau membru. */
  String PART_OF_GRANT =
      """
      (g.coordonator_id = :userId
       OR EXISTS (SELECT 1 FROM membru_grant m WHERE m.grant_id = g.id AND m.utilizator_id = :userId))
      """;

  @Query(value = WITH_BALANCE + " ORDER BY g.cod", nativeQuery = true)
  List<GrantBalance> findAllWithBalance();

  @Query(value = WITH_BALANCE + " WHERE " + PART_OF_GRANT + " ORDER BY g.cod", nativeQuery = true)
  List<GrantBalance> findAllWithBalanceForUser(@Param("userId") long userId);

  @Query(value = WITH_BALANCE + " WHERE g.id = :grantId", nativeQuery = true)
  Optional<GrantBalance> findWithBalanceById(@Param("grantId") long grantId);

  @Query(
      value =
          "SELECT EXISTS (SELECT 1 FROM grant_cercetare g WHERE g.id = :grantId AND "
              + PART_OF_GRANT
              + ")",
      nativeQuery = true)
  boolean isPartOfGrant(@Param("grantId") long grantId, @Param("userId") long userId);
}
