# CLAUDE.md — rrm-backend

API REST pentru **Research Resource Manager**: platforma de rezervare a resurselor de cercetare (echipamente, roboți, laboratoare), cu rezervări fără suprapuneri, aprobare pe 2 niveluri, check-in și rapoarte pe granturi.

- Frontend: `../rrm-frontend` (React 18 + TypeScript)
- Organizație: https://github.com/student-team-origin
- Confluence (spațiul proiectului): PRD, *Document de Arhitectură*, *Ghid de Dezvoltare*

## Stack
Java 21 · Spring Boot 4.1 · Lombok · Maven (`./mvnw`) · Spring Security + JWT · Spring Data JPA · PostgreSQL · Flyway · JUnit 5 + Mockito

## Surse de adevăr
- **`api-contract/openapi.yaml`** este contractul API. `api-contract/` este **submodulul git** [rrm-api-contract](https://github.com/student-team-origin/rrm-api-contract), comun cu frontend-ul.
  - **Nu edita fișierul din backend.** Orice endpoint nou sau modificat se schimbă **întâi** în `rrm-api-contract`, printr-un PR separat (regulile sunt în `api-contract/CLAUDE.md`).
  - După merge-ul acelui PR, pe ramura tichetului din backend se actualizează pointerul submodulului:
    ```bash
    git -C api-contract fetch && git -C api-contract switch --detach origin/main
    git add api-contract
    ```
    Acesta intră în același commit cu codul care implementează modificarea.
  - Dacă `api-contract/` e gol după clonare: `git submodule update --init`.
- **`src/main/resources/db/migration/`** conține migrările Flyway (`V1__schema.sql` ...).
- Diagramele de domeniu, componente și secvență sunt în Confluence. Structura codului trebuie să le urmeze.

---

## Flux de lucru (obligatoriu)

### 1. Fiecare modificare are un tichet Jira
- Cheia proiectului este **`TO`**. Nu începe nicio modificare de cod fără o cheie de tichet.
- **Dacă utilizatorul nu a dat o cheie Jira, întreabă-l care este, înainte de a crea ramura.** Nu inventa chei.

Tichete existente (din PRD):

| Cheie | Funcționalitate |
|---|---|
| TO-1 | Gestiunea sesiunilor și a tokenurilor (60 min inactivitate) |
| TO-2 | Autentificare în doi pași (2FA, dispozitiv de încredere 5 zile) |
| TO-3 | RBAC: Student, Coordonator Grant, Responsabil Laborator, Administrator Sistem |
| TO-4 | Catalog logic de resurse |
| TO-5 | Motor de rezervare fără suprapuneri (hard lock + buffer time) |
| TO-6 | Flux de aprobare configurabil |
| TO-7 | Check-in prin interfața web și auto-anulare |
| TO-8 | Dashboard de utilizare și grad de ocupare |
| TO-9 | Motor de notificări (e-mail + dashboard) |
| TO-11 | Integrare SSO universitar |
| TO-17 | Export rapoarte audit (PDF / Excel) |

### 2. Ramuri
- `main` este protejată: **nu face niciodată commit sau push direct pe `main`**. Totul intră prin Pull Request.
- Înainte de o ramură nouă, actualizează `main`: `git switch main && git pull && git submodule update`.
- Format: `<tip>/TO-<nr>-<descriere-scurta-kebab>`
  - `feature/TO-4-catalog-resurse`
  - `fix/TO-5-suprapunere-buffer`
  - `chore/TO-12-configurare-ci`
- Tipuri de ramură: `feature`, `fix`, `refactor`, `chore`, `docs`, `test`.
- O ramură corespunde unui singur tichet. Nu amesteca mai multe tichete pe aceeași ramură.

### 3. Commit-uri (Conventional Commits + cheia Jira)
```
<tip>(<modul>): TO-<nr> <descriere la imperativ, în română>
```
- `feat(rezervari): TO-5 verificare suprapunere cu buffer time`
- `fix(auth): TO-1 invalidare token la logout`
- `test(granturi): TO-5 teste pentru sold insuficient`

Tipuri: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`. Modulul este numele pachetului de domeniu (`rezervari`, `resurse`, `auth`...).

Commit-urile sunt mici și coerente. Nu include fișiere generate, `.env` sau secrete.

### 4. Pull Request
- Titlu: `TO-<nr> <Titlul tichetului>`, de exemplu `TO-4 Catalog logic de resurse`.
- Descrierea conține: link-ul tichetului Jira, **ce** s-a schimbat, **cum** s-a testat și dacă s-a actualizat submodulul `api-contract` (cu link spre PR-ul din `rrm-api-contract`) sau s-au adăugat migrări.
- Este nevoie de minim **1 aprobare**. Comentariile se rezolvă înainte de merge.
- Merge: **Squash and merge**, apoi ramura se șterge.
- După merge: `git switch main && git pull`, apoi verifici că aplicația pornește.

### 5. Înainte de commit / PR
```bash
./mvnw spotless:apply      # formatare Google Java Style
./mvnw verify              # build + Checkstyle + teste
```
Nu deschide PR dacă `verify` eșuează.

---

## Coding style

**Ghid:** [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), aplicat automat cu **Spotless** (google-java-format) și **Checkstyle** (`google_checks.xml` + excepțiile din `config/checkstyle/suppressions.xml`). Indentare de 2 spații, linii de maximum 100 de caractere. Nu adăuga suprimări noi fără acordul echipei.

### Limba
- **Codul este în engleză**: clase, metode, variabile, path-uri API.
- **Tabelele și coloanele din DB rămân în română**, ca în `V1__schema.sql`. Maparea se face explicit:
  ```java
  @Entity
  @Table(name = "rezervare")
  public class Reservation {
    @Column(name = "ore_solicitate")
    private BigDecimal requestedHours;
  }
  ```
- Mesajele de eroare pentru utilizator sunt în română. Commit-urile sunt în română.
- Fără diacritice în identificatori.

Glosar:

| Română | Engleză |
|---|---|
| Utilizator | `User` |
| Grant (cercetare) | `Grant` |
| Membru grant | `GrantMember` |
| Laborator | `Laboratory` |
| Resursă | `Resource` |
| Rezervare | `Reservation` |
| Aprobare | `Approval` |
| Flux aprobare / Pas | `ApprovalFlow` / `ApprovalStep` |
| Notificare | `Notification` |
| Atașament | `Attachment` |

### Structura pachetelor (pe module de domeniu)
```
ro.usv.rrm
├── auth/            JWT, 2FA, filtru de securitate, dispozitiv de încredere
├── user/
├── grant/
├── laboratory/
├── resource/
├── reservation/
├── approval/
├── notification/
├── report/
├── scheduler/       memento-uri, auto-anulare fără check-in
└── common/
    ├── config/      RrmProperties, WebConfig, SecurityConfig
    ├── error/       ApiError, ApiException, GlobalExceptionHandler
    └── web/         PageResponse
```
Fiecare modul de domeniu conține: `XController`, `XService`, `XRepository`, `X` (entitate), `dto/`.

### Infrastructura comună (folosește-o, nu o duplica)
- **Prefixul `/api/v1` se adaugă automat** tuturor `@RestController` (`WebConfig`). Controllerul declară doar `@RequestMapping("/resources")`.
- **Erori:** aruncă `ApiException` din `common.error`, prin metodele de fabrică `notFound` (404), `conflict` (409) sau `businessRule` (422), cu un cod stabil în `UPPER_SNAKE_CASE` în română și un mesaj pentru utilizator:
  ```java
  throw ApiException.notFound("RESURSA_INEXISTENTA", "Resursa cu id-ul " + id + " nu există.");
  ```
  Nu crea clase de excepții noi pentru fiecare caz. Validarea `@Valid` produce automat 400 cu `fields`.
- **Configurație:** setările proprii stau în secțiunea `rrm:` din `application.yml` și se citesc prin `RrmProperties` (`record` cu `@ConfigurationProperties`), injectat în constructor. Nu folosi `@Value`.
- **Liste paginate:** întoarce `PageResponse.from(page, ResourceResponse::from)`. Forma JSON (`items`, `page`, `size`, `total`) este cea din `openapi.yaml`.
- **Securitatea** este momentan deschisă (`permitAll` în `SecurityConfig`) până la TO-1/TO-3.
- **Câmpurile JSON urmează `openapi.yaml`** (în română: `denumire`, `categorieId`, `stare`...). Numele componentelor record din DTO-uri sunt cele din contract, chiar dacă clasele și restul codului sunt în engleză.

### Reguli
- **Straturi:** Controller → Service → Repository. Controllerul nu conține logică și nu folosește direct un Repository. Un Service poate apela alte Service-uri, dar nu Repository-urile altui modul.
- **DTO-uri** ca `record` (`CreateReservationRequest`, `ReservationResponse`). Entitățile JPA nu se expun niciodată în API.
- **Fără boilerplate:**
  - **Lombok** pentru entități și clasele Spring, nu getteri, setteri sau constructori scriși de mână;
  - **`record`** pentru DTO-uri și configurații.
- **Injecție prin constructor** cu câmpuri `private final` + `@RequiredArgsConstructor`. Fără `@Autowired` pe câmpuri.
- **Logging** cu `@Slf4j` (Lombok).
- **`@Transactional`** se pune pe metodele din Service. Verificarea suprapunerii, verificarea soldului și salvarea cererii se fac în **aceeași tranzacție** (hard lock, TO-5).
- **Hard lock-ul este garantat de baza de date**, nu doar de cod:
  - constrângerea `rezervare_fara_suprapunere` (`EXCLUDE USING gist` pe `tstzrange(start, sfarsit_blocat)`);
  - triggerul care calculează `sfarsit_blocat` (sfârșit + timpul de pregătire).
  
  Nu le elimina și nu le ocoli. Verificarea din Service servește doar ca să întoarcă un mesaj clar. Încălcarea constrângerii (`DataIntegrityViolationException`) se transformă tot în **409**.
- **Erori:** se aruncă `ApiException`, tratată în `GlobalExceptionHandler`. Codurile HTTP trebuie să corespundă cu `openapi.yaml`:
  - 404: nu există;
  - 409: interval ocupat;
  - 422: sold insuficient sau regulă de business încălcată;
  - 403: RBAC.
- **Validare** a input-ului cu Jakarta Validation (`@Valid`, `@NotNull`...) pe DTO-uri.
- **Date și ore:** `OffsetDateTime`/`Instant`, niciodată `java.util.Date`.
- **Entități JPA** (model: `resource/Resource.java`):
  - `@Getter`, `@Setter` și `@NoArgsConstructor(access = AccessLevel.PROTECTED)` pe clasă, plus un constructor public cu câmpurile obligatorii;
  - id-ul nu are setter (`@Setter(AccessLevel.NONE)`);
  - **fără `@Data`, `@EqualsAndHashCode` sau `@ToString`** pe entități: încarcă asocierile LAZY și pot intra în buclă;
  - asocierile `@ManyToOne` sunt `FetchType.LAZY`, deci se accesează doar în interiorul unei tranzacții (Service), nu în Controller (`open-in-view` este dezactivat);
  - coloanele `text` au `columnDefinition = "text"`, iar cele `smallint` au `@JdbcTypeCode(SqlTypes.SMALLINT)`. Altfel validarea schemei (`ddl-auto: validate`) eșuează la pornire.
- **Enumerări din contract** (ex. `ResourceStatus`): constantele sunt în stil Java (`ACTIVE`), iar valoarea din DB și JSON este cea din `openapi.yaml` (`"Activa"`). Legătura se face prin `@JsonValue`/`@JsonCreator` și un `AttributeConverter` cu `autoApply = true`.
- **Denumiri:**
  - clase: `PascalCase`;
  - metode și variabile: `camelCase`;
  - constante: `UPPER_SNAKE_CASE`;
  - pachete: litere mici.
- **Comentarii:** Javadoc pe metodele publice din Service. Comentariile din corpul metodelor explică *de ce*, nu *ce*.
- **Securitate:** fără secrete în cod. Acestea se citesc din variabile de mediu (`application.yml` + `${...}`). Nu loga tokenuri, parole sau coduri 2FA.

### Migrări Flyway
- **Nu modifica o migrare deja ajunsă pe `main`.** Orice schimbare de schemă se face într-o migrare nouă: `V<n+1>__descriere.sql`.
- Numele tabelelor și coloanelor din migrări rămân în română, `snake_case`.

### Teste
- JUnit 5 + Mockito pentru Service, `@WebMvcTest` pentru Controller, `@DataJpaTest` pentru Repository.
- Numele testelor: `method_condition_expectedResult`, de exemplu `create_overlappingSlot_throwsConflict`.
- Orice regulă de business nouă (suprapunere, sold, tranziții de stare) are cel puțin un test.

---

## Comenzi utile
```bash
./mvnw spring-boot:run     # pornește API-ul pe http://localhost:8080/api/v1 (baza rrm_dev trebuie să existe)
./mvnw test                # doar testele
./mvnw spotless:apply      # formatare
./mvnw verify              # verificarea completă dinainte de PR
```
