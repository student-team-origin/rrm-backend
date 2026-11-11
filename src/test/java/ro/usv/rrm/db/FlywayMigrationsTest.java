package ro.usv.rrm.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifică migrările Flyway pe baza de date locală: regulile critice din schemă (hard lock, timpul
 * de pregătire) trebuie să funcționeze independent de codul Java.
 *
 * <p>Fiecare test rulează într-o tranzacție anulată la final, deci datele de test rămân neatinse.
 */
@SpringBootTest
@Transactional
class FlywayMigrationsTest {

  /** Resursa 1 din datele de test are timp de pregătire de 15 minute. */
  private static final long RESOURCE_ID = 1;

  private static final long USER_ID = 7;
  private static final long GRANT_ID = 1;

  /** O zi îndepărtată, fără rezervări în datele de test. */
  private static final OffsetDateTime DAY = OffsetDateTime.parse("2030-01-10T00:00:00+02:00");

  @Autowired private JdbcTemplate jdbc;

  @Test
  void migrations_areAppliedSuccessfully() {
    List<String> versions =
        jdbc.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
            String.class);

    assertThat(versions).contains("1", "2");
  }

  @Test
  void testData_isLoaded() {
    assertThat(count("resursa")).isPositive();
    assertThat(count("laborator")).isPositive();
    assertThat(count("categorie_resursa")).isPositive();
  }

  @Test
  void insertReservation_setsBlockedEndWithPreparationTime() {
    long id = insertReservation("Aprobata", DAY.withHour(10), DAY.withHour(12));

    OffsetDateTime blockedEnd =
        jdbc.queryForObject(
            "SELECT sfarsit_blocat FROM rezervare WHERE id = ?", OffsetDateTime.class, id);

    assertThat(blockedEnd).isEqualTo(DAY.withHour(12).plusMinutes(15));
  }

  @Test
  void insertReservation_overlappingPreparationTime_isRejected() {
    insertReservation("Aprobata", DAY.withHour(10), DAY.withHour(12));

    // Începe la 12:10, în intervalul de pregătire (12:00–12:15) al rezervării anterioare.
    assertThatThrownBy(() ->
            insertReservation(
                "InAsteptareCoordonator", DAY.withHour(12).plusMinutes(10), DAY.withHour(13)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("rezervare_fara_suprapunere");
  }

  @Test
  void insertReservation_startingRightAfterPreparationTime_isAccepted() {
    insertReservation("Aprobata", DAY.withHour(10), DAY.withHour(12));

    insertReservation("Aprobata", DAY.withHour(12).plusMinutes(15), DAY.withHour(13));

    assertThat(countReservationsOn(DAY)).isEqualTo(2);
  }

  @Test
  void insertReservation_overlappingCancelledReservation_isAccepted() {
    insertReservation("Anulata", DAY.withHour(10), DAY.withHour(12));

    insertReservation("Aprobata", DAY.withHour(11), DAY.withHour(13));

    assertThat(countReservationsOn(DAY)).isEqualTo(2);
  }

  private long insertReservation(String status, OffsetDateTime start, OffsetDateTime end) {
    return jdbc.queryForObject(
        """
        INSERT INTO rezervare (solicitant_id, resursa_id, grant_id, stare, start, sfarsit,
                               sfarsit_blocat, ore_solicitate, scop)
        VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'Test migrare')
        RETURNING id
        """,
        Long.class,
        USER_ID,
        RESOURCE_ID,
        GRANT_ID,
        status,
        start,
        end,
        end); // sfarsit_blocat este recalculat de trigger
  }

  private long countReservationsOn(OffsetDateTime day) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM rezervare WHERE resursa_id = ? AND start >= ? AND start < ?",
        Long.class,
        RESOURCE_ID,
        day,
        day.plusDays(1));
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
}
