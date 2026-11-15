package ro.usv.rrm.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Generated;

/**
 * Un utilizator al platformei (student, coordonator de grant, responsabil de laborator sau
 * administrator). Rolurile, cu scopul lor, se află în tabela {@code utilizator_rol}.
 */
@Entity
@Table(name = "utilizator")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(name = "nume", nullable = false, length = 120)
  private String name;

  /**
   * Mereu normalizat (vezi {@link #normalizeEmail}); baza de date o impune printr-un CHECK (V3).
   */
  @Column(name = "email", nullable = false, unique = true, length = 160)
  @Setter(AccessLevel.NONE)
  private String email;

  /** Hash-ul BCrypt al parolei. Parola în clar nu se păstrează niciodată. */
  @Column(name = "parola_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "totp_secret", length = 64)
  private String totpSecret;

  @Column(name = "doi_factori_activ", nullable = false)
  private boolean twoFactorEnabled;

  @Column(name = "activ", nullable = false)
  private boolean active = true;

  /** Rolurile utilizatorului, cu scopul fiecăruia. Se modifică doar prin {@link UserRole}. */
  // Într-o listă de utilizatori, rolurile se încarcă în loturi, nu cu o interogare per utilizator.
  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  @BatchSize(size = 100)
  @Setter(AccessLevel.NONE)
  private List<UserRole> roles = new ArrayList<>();

  /** Completat de baza de date ({@code DEFAULT now()}) la inserare. */
  @Generated
  @Column(name = "data_creare", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Instant createdAt;

  public User(String name, String email, String passwordHash) {
    this.name = name;
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
  }

  public void setEmail(String email) {
    this.email = normalizeEmail(email);
  }

  /**
   * Forma în care se păstrează și se caută e-mailurile: fără spații la capete, cu litere mici. Așa
   * unicitatea și login-ul nu depind de literele mari sau mici introduse.
   */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
