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
| Framework | Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation) |
| Bază de date | PostgreSQL 16+ + Flyway (migrări) |
| Autentificare | JWT, 2FA (TOTP / cod pe e-mail), RBAC |
| Build | Maven (wrapper `./mvnw`) |
| Testare | JUnit 5, Mockito |
| Calitate cod | Spotless (google-java-format), Checkstyle |

## Cerințe
- JDK 21 sau mai nou (proiectul se compilează pentru Java 21)
- PostgreSQL 16 sau mai nou: [Postgres.app](https://postgresapp.com/) pe macOS, instalarea oficială pe Windows/Linux, sau Docker
- Maven nu trebuie instalat — se folosește wrapper-ul `./mvnw` (pe Windows `mvnw.cmd`)

## Pornire locală

1. **Creează utilizatorul și baza de date** (o singură dată):
   ```bash
   psql -d postgres -c "CREATE ROLE rrm LOGIN PASSWORD 'rrm';"
   psql -d postgres -c "CREATE DATABASE rrm_dev OWNER rrm;"
   ```
   Alternativ, cu Docker:
   ```bash
   docker run -d --name rrm-db -e POSTGRES_DB=rrm_dev -e POSTGRES_USER=rrm -e POSTGRES_PASSWORD=rrm -p 5432:5432 postgres:16
   ```
2. **Pornește aplicația:**
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway aplică automat migrările la pornire. API-ul răspunde la `http://localhost:8080/api/v1`.

Valorile implicite (din `application.yml`) se potrivesc cu pașii de mai sus. Dacă baza ta e configurată altfel, le suprascrii prin variabile de mediu:

| Variabilă | Implicit |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/rrm_dev` |
| `DB_USER` / `DB_PASSWORD` | `rrm` / `rrm` |
| `PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (frontend-ul Vite) |

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
│   ├── config/      WebConfig (prefix /api/v1), SecurityConfig (CORS, securitate)
│   ├── error/       ApiError, excepții de domeniu, GlobalExceptionHandler
│   └── web/         PageResponse (răspuns paginat)
├── auth/  user/  grant/  laboratory/  resource/      (module de domeniu)
├── reservation/  approval/  notification/  report/
└── scheduler/
src/main/resources/
├── application.yml
└── db/migration/        V1__schema.sql, V2__...
config/checkstyle/       excepțiile echipei de la google_checks.xml
docs/
└── openapi.yaml         contractul API
```
Fiecare modul de domeniu are straturile Controller → Service → Repository, conform diagramei de componente. Modulele se adaugă pe măsură ce sunt implementate.

## Documentație API
Specificația OpenAPI se află în [`docs/openapi.yaml`](docs/openapi.yaml). Frontend-ul își generează tipurile TypeScript din acest fișier.

## Contribuție
- Fiecare modificare pornește de la un tichet Jira (`TO-<nr>`).
- Ramurile au forma `feature/TO-<nr>-descriere`. Nu se face push direct pe `main`.
- Commit-urile au forma `feat(modul): TO-<nr> descriere`.
- PR-ul are nevoie de minim 1 aprobare și se integrează cu **Squash and merge**.
- Stilul de cod este [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

Regulile complete sunt în [`CLAUDE.md`](CLAUDE.md) și în pagina **Ghid de Dezvoltare** din Confluence.

## Echipa
Vladislav Shorodok · Tudor Baciu · Yaroslav Chernov · Erick
