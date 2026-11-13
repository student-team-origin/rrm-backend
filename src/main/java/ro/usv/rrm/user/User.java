package ro.usv.rrm.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un utilizator al platformei (student, coordonator de grant, responsabil de laborator sau
 * administrator). Rolurile, cu scopul lor, se află în tabela {@code utilizator_rol} (TO-3).
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

  @Column(name = "email", nullable = false, unique = true, length = 160)
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

  @Column(name = "data_creare", nullable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private Instant createdAt = Instant.now();

  public User(String name, String email, String passwordHash) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
  }
}
