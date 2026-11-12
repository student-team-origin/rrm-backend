# rrm-backend

API REST pentru **Research Resource Manager**: o platformă centralizată pentru gestionarea și rezervarea resurselor de cercetare (echipamente speciale, roboți educaționali, spații de laborator).

Funcționalități principale:
- **rezervări fără suprapuneri**: hard lock tranzacțional, inclusiv timpul tehnic de pregătire;
- **aprobare pe 2 niveluri**: Coordonator Grant, apoi Responsabil Laborator;
- **check-in** cu auto-anulare;
- **notificări** și **rapoarte** de utilizare pe granturi.

> Interfața web se află în [rrm-frontend](https://github.com/student-team-origin/rrm-frontend).

## Tehnologii

| Categorie | Tehnologie |
|---|---|
| Limbaj | Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation), Lombok |
| Bază de date | PostgreSQL 16+ + Flyway (migrări) |
| Autentificare | JWT, 2FA (TOTP / cod pe e-mail), RBAC |
| Build | Maven (wrapper `./mvnw`) |
| Testare | JUnit 5, Mockito |
| Calitate cod | Spotless (google-java-format), Checkstyle |

## Cerințe
- JDK 21 sau mai nou (proiectul se compilează pentru Java 21)
- PostgreSQL 16 sau mai nou: [Postgres.app](https://postgresapp.com/) pe macOS, instalarea oficială pe Windows/Linux, sau Docker
- Maven nu trebuie instalat — se folosește wrapper-ul `./mvnw` (pe Windows `mvnw.cmd`)

## Clonare

Contractul API este inclus ca submodul git (`api-contract/`), deci clonarea se face cu:
```bash
git clone --recurse-submodules https://github.com/student-team-origin/rrm-backend.git
```
Dacă ai clonat deja fără submodule:
```bash
git submodule update --init
```
După fiecare `git pull`, rulează și `git submodule update`.

## Pornire rapidă

Aplicația completă are 3 componente, pornite în ordinea: **PostgreSQL → backend → [frontend](https://github.com/student-team-origin/rrm-frontend#pornire-rapidă)**.

1. **Baza de date** (o singură dată). Alege una dintre variante:
   - cu Docker, din rădăcina acestui repo:
     ```bash
     docker compose up -d
     ```
   - cu un PostgreSQL instalat local (Postgres.app, instalarea oficială etc.):
     ```bash
     psql -d postgres -c "CREATE ROLE rrm LOGIN PASSWORD 'rrm';"
     psql -d postgres -c "CREATE DATABASE rrm_dev OWNER rrm;"
     ```
2. **Backend-ul:**
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway creează tabelele și încarcă datele de test la prima pornire. Pornirea s-a încheiat când în consolă apare `Started RrmBackendApplication`.
3. **Verificare:**
   ```bash
   curl http://localhost:8080/api/v1/resource-categories
   ```
   Trebuie să întoarcă 4 categorii (Echipament, Nod de calcul, Robot educațional, Spațiu de laborator).
4. **Frontend-ul:** vezi [Pornire rapidă în rrm-frontend](https://github.com/student-team-origin/rrm-frontend#pornire-rapidă).

Probleme frecvente:
- `Connection refused` la pornire: PostgreSQL nu rulează (pornește Postgres.app sau `docker compose up -d`).
- `Port 8080 was already in use`: un backend pornit anterior rulează încă; oprește-l sau pornește cu `PORT=8081 ./mvnw spring-boot:run` (atunci schimbă și proxy-ul din `vite.config.ts` al frontend-ului).

Valorile implicite (din `application.yml`) se potrivesc cu pașii de mai sus. Dacă baza ta e configurată altfel, le suprascrii prin variabile de mediu:

| Variabilă | Implicit |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/rrm_dev` |
| `DB_USER` / `DB_PASSWORD` | `rrm` / `rrm` |
| `PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (frontend-ul Vite) |

## Baza de date

Schema este gestionată de Flyway, prin fișierele din `src/main/resources/db/migration/`:

| Migrare | Conținut |
|---|---|
| `V1__schema.sql` | Tabelele, constrângerile (inclusiv hard lock-ul rezervărilor), triggerul pentru timpul de pregătire, indecșii și view-ul `v_sold_grant` |
| `V2__date_test.sql` | Date de test: utilizatori (parola tuturor: `parola123`), granturi, 2 laboratoare, 4 categorii, 6 resurse, 7 rezervări relative la ziua curentă |

- O schimbare de schemă se face **doar printr-o migrare nouă** (`V3__descriere.sql`). Migrările ajunse pe `main` nu se modifică.
- Pentru a reseta baza locală la datele de test (de exemplu înainte de demo), **oprește întâi backend-ul** (altfel `DROP DATABASE` eșuează pentru că baza e în uz), apoi:
  ```bash
  psql -d postgres -c "DROP DATABASE rrm_dev;" -c "CREATE DATABASE rrm_dev OWNER rrm;"
  ```
  Cu Docker: `docker compose down -v && docker compose up -d`.
  La următoarea pornire, Flyway aplică din nou toate migrările.

## Comenzi

| Comandă | Ce face |
|---|---|
| `./mvnw spring-boot:run` | Pornește API-ul |
| `./mvnw test` | Rulează testele |
| `./mvnw spotless:apply` | Formatează codul după Google Java Style |
| `./mvnw verify` | Build + teste + Spotless check + Checkstyle (obligatoriu înainte de PR; are nevoie de baza de date pornită) |

## Structura proiectului
```
src/main/java/ro/usv/rrm/
├── common/
│   ├── config/      RrmProperties (setările rrm.*), WebConfig (prefix /api/v1), SecurityConfig
│   ├── error/       ApiError, ApiException, GlobalExceptionHandler
│   └── web/         PageResponse (răspuns paginat)
├── auth/  user/  grant/  laboratory/  resource/      (module de domeniu)
├── reservation/  approval/  notification/  report/
└── scheduler/
src/main/resources/
├── application.yml
└── db/migration/        V1__schema.sql, V2__...
config/checkstyle/       excepțiile echipei de la google_checks.xml
api-contract/            submodul git → rrm-api-contract (openapi.yaml)
```
Fiecare modul de domeniu are straturile Controller → Service → Repository, conform diagramei de componente. Modulele se adaugă pe măsură ce sunt implementate.

## Documentație API
Specificația OpenAPI se află în repository-ul separat [rrm-api-contract](https://github.com/student-team-origin/rrm-api-contract), inclus aici ca submodul la `api-contract/openapi.yaml`. Același submodul este folosit de frontend pentru a-și genera tipurile TypeScript. Contractul se modifică doar prin PR în `rrm-api-contract`.

## Contribuție
- Fiecare modificare pornește de la un tichet Jira (`TO-<nr>`).
- Ramurile au forma `feature/TO-<nr>-descriere`. Nu se face push direct pe `main`.
- Commit-urile au forma `feat(modul): TO-<nr> descriere`.
- PR-ul are nevoie de minim 1 aprobare și se integrează cu **Squash and merge**.
- Stilul de cod este [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

Regulile complete sunt în [`CLAUDE.md`](CLAUDE.md) și în pagina **Ghid de Dezvoltare** din Confluence.

## Echipa
Vladislav Shorodok · Tudor Baciu · Yaroslav Chernov · Erick
