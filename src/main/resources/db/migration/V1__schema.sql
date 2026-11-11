-- =====================================================================
-- Research Resource Manager - schema bazei de date (PostgreSQL 16)
-- Flyway: src/main/resources/db/migration/V1__schema.sql
-- =====================================================================

-- Necesară pentru constrângerea de excludere (hard lock) pe rezervări
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Utilizatori, roluri, securitate
-- ---------------------------------------------------------------------
CREATE TABLE utilizator (
    id                 BIGSERIAL    PRIMARY KEY,
    nume               VARCHAR(120) NOT NULL,
    email              VARCHAR(160) NOT NULL UNIQUE,
    parola_hash        VARCHAR(100) NOT NULL,
    totp_secret        VARCHAR(64),
    doi_factori_activ  BOOLEAN      NOT NULL DEFAULT FALSE,
    activ              BOOLEAN      NOT NULL DEFAULT TRUE,
    data_creare        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE utilizator_rol (
    id             BIGSERIAL   PRIMARY KEY,
    utilizator_id  BIGINT      NOT NULL REFERENCES utilizator(id) ON DELETE CASCADE,
    rol            VARCHAR(30) NOT NULL
        CHECK (rol IN ('Student', 'CoordonatorGrant', 'ResponsabilLaborator', 'AdministratorSistem')),
    tip_scop       VARCHAR(20) NOT NULL
        CHECK (tip_scop IN ('Global', 'Grant', 'Laborator')),
    scop_id        BIGINT,
    -- rolul global nu are scop; rolurile pe grant / laborator au obligatoriu un scop
    CHECK ((tip_scop = 'Global') = (scop_id IS NULL)),
    UNIQUE (utilizator_id, rol, tip_scop, scop_id)
);

CREATE TABLE sesiune (
    id                 BIGSERIAL   PRIMARY KEY,
    utilizator_id      BIGINT      NOT NULL REFERENCES utilizator(id) ON DELETE CASCADE,
    jti                UUID        NOT NULL UNIQUE,
    ultima_activitate  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_la          TIMESTAMPTZ NOT NULL,
    revocata           BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE dispozitiv_incredere (
    id             BIGSERIAL    PRIMARY KEY,
    utilizator_id  BIGINT       NOT NULL REFERENCES utilizator(id) ON DELETE CASCADE,
    token_hash     VARCHAR(100) NOT NULL UNIQUE,
    expira_la      TIMESTAMPTZ  NOT NULL
);

-- ---------------------------------------------------------------------
-- Granturi
-- ---------------------------------------------------------------------
-- "grant" este cuvânt rezervat în SQL, de aceea tabela se numește grant_cercetare
CREATE TABLE grant_cercetare (
    id              BIGSERIAL     PRIMARY KEY,
    cod             VARCHAR(40)   NOT NULL UNIQUE,
    coordonator_id  BIGINT        NOT NULL REFERENCES utilizator(id),
    ore_alocate     NUMERIC(7,2)  NOT NULL CHECK (ore_alocate >= 0),
    data_start      DATE          NOT NULL,
    data_sfarsit    DATE          NOT NULL,
    CHECK (data_sfarsit >= data_start)
);

CREATE TABLE membru_grant (
    grant_id       BIGINT NOT NULL REFERENCES grant_cercetare(id) ON DELETE CASCADE,
    utilizator_id  BIGINT NOT NULL REFERENCES utilizator(id) ON DELETE CASCADE,
    data_aderare   DATE   NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (grant_id, utilizator_id)
);

-- ---------------------------------------------------------------------
-- Laboratoare și resurse
-- ---------------------------------------------------------------------
CREATE TABLE laborator (
    id              BIGSERIAL   PRIMARY KEY,
    denumire        VARCHAR(80) NOT NULL,
    locatie         VARCHAR(80) NOT NULL,
    responsabil_id  BIGINT      NOT NULL REFERENCES utilizator(id)
);

CREATE TABLE categorie_resursa (
    id        BIGSERIAL   PRIMARY KEY,
    denumire  VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE resursa (
    id                  BIGSERIAL    PRIMARY KEY,
    laborator_id        BIGINT       NOT NULL REFERENCES laborator(id),
    categorie_id        BIGINT       NOT NULL REFERENCES categorie_resursa(id),
    denumire            VARCHAR(120) NOT NULL,
    specificatii        TEXT,
    stare               VARCHAR(20)  NOT NULL DEFAULT 'Activa'
        CHECK (stare IN ('Activa', 'Mentenanta', 'Defecta')),
    timp_pregatire_min  SMALLINT     NOT NULL DEFAULT 0 CHECK (timp_pregatire_min >= 0),
    reguli_acces        TEXT
);

CREATE TABLE atasament (
    id              BIGSERIAL    PRIMARY KEY,
    resursa_id      BIGINT       NOT NULL REFERENCES resursa(id) ON DELETE CASCADE,
    nume_fisier     VARCHAR(200) NOT NULL,
    cale            VARCHAR(300) NOT NULL,
    data_incarcare  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Rezervări (hard lock)
-- ---------------------------------------------------------------------
CREATE TABLE rezervare (
    id              BIGSERIAL    PRIMARY KEY,
    solicitant_id   BIGINT       NOT NULL REFERENCES utilizator(id),
    resursa_id      BIGINT       NOT NULL REFERENCES resursa(id),
    grant_id        BIGINT       NOT NULL REFERENCES grant_cercetare(id),
    stare           VARCHAR(30)  NOT NULL DEFAULT 'InAsteptareCoordonator'
        CHECK (stare IN ('InAsteptareCoordonator', 'InAsteptareLaborator', 'Aprobata', 'Activa',
                         'Finalizata', 'Respinsa', 'Anulata', 'AnulataAutomat')),
    start           TIMESTAMPTZ  NOT NULL,
    sfarsit         TIMESTAMPTZ  NOT NULL,
    sfarsit_blocat  TIMESTAMPTZ  NOT NULL,   -- sfarsit + timpul de pregătire al resursei (setat de trigger)
    ore_solicitate  NUMERIC(5,2) NOT NULL CHECK (ore_solicitate > 0),
    scop            TEXT         NOT NULL,
    data_creare     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    data_check_in   TIMESTAMPTZ,
    CHECK (sfarsit > start),
    CHECK (sfarsit_blocat >= sfarsit),
    -- HARD LOCK: două rezervări active pe aceeași resursă nu se pot suprapune,
    -- nici măcar dacă sunt inserate simultan
    CONSTRAINT rezervare_fara_suprapunere EXCLUDE USING gist (
        resursa_id WITH =,
        tstzrange(start, sfarsit_blocat, '[)') WITH &&
    ) WHERE (stare IN ('InAsteptareCoordonator', 'InAsteptareLaborator', 'Aprobata', 'Activa'))
);

-- Calculează automat sfarsit_blocat = sfarsit + timp_pregatire_min al resursei
CREATE FUNCTION fn_rezervare_sfarsit_blocat() RETURNS trigger AS $$
BEGIN
    SELECT NEW.sfarsit + make_interval(mins => r.timp_pregatire_min)
      INTO NEW.sfarsit_blocat
      FROM resursa r
     WHERE r.id = NEW.resursa_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_rezervare_sfarsit_blocat
    BEFORE INSERT OR UPDATE OF resursa_id, sfarsit ON rezervare
    FOR EACH ROW EXECUTE FUNCTION fn_rezervare_sfarsit_blocat();

-- ---------------------------------------------------------------------
-- Aprobări (flux fix pe 2 niveluri)
-- ---------------------------------------------------------------------
CREATE TABLE aprobare (
    id             BIGSERIAL   PRIMARY KEY,
    rezervare_id   BIGINT      NOT NULL REFERENCES rezervare(id) ON DELETE CASCADE,
    nivel          SMALLINT    NOT NULL CHECK (nivel IN (1, 2)),  -- 1 = Coordonator Grant, 2 = Responsabil Laborator
    aprobator_id   BIGINT      NOT NULL REFERENCES utilizator(id),
    decizie        VARCHAR(10) NOT NULL CHECK (decizie IN ('Aprobat', 'Respins')),
    observatie     TEXT,
    data_decizie   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (rezervare_id, nivel),
    -- respingerea necesită un motiv
    CHECK (decizie = 'Aprobat' OR observatie IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- Notificări
-- ---------------------------------------------------------------------
CREATE TABLE notificare (
    id             BIGSERIAL   PRIMARY KEY,
    utilizator_id  BIGINT      NOT NULL REFERENCES utilizator(id) ON DELETE CASCADE,
    rezervare_id   BIGINT      REFERENCES rezervare(id) ON DELETE SET NULL,
    tip            VARCHAR(20) NOT NULL
        CHECK (tip IN ('SchimbareStare', 'Memento', 'Mentenanta', 'Urgenta')),
    mesaj          TEXT        NOT NULL,
    citita         BOOLEAN     NOT NULL DEFAULT FALSE,
    data_creare    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Indexuri pentru cheile străine și căutările frecvente
-- ---------------------------------------------------------------------
CREATE INDEX idx_utilizator_rol_utilizator ON utilizator_rol(utilizator_id);
CREATE INDEX idx_sesiune_utilizator        ON sesiune(utilizator_id);
CREATE INDEX idx_dispozitiv_utilizator     ON dispozitiv_incredere(utilizator_id);
CREATE INDEX idx_grant_coordonator         ON grant_cercetare(coordonator_id);
CREATE INDEX idx_membru_grant_utilizator   ON membru_grant(utilizator_id);
CREATE INDEX idx_laborator_responsabil     ON laborator(responsabil_id);
CREATE INDEX idx_resursa_laborator         ON resursa(laborator_id);
CREATE INDEX idx_resursa_categorie         ON resursa(categorie_id);
CREATE INDEX idx_atasament_resursa         ON atasament(resursa_id);
CREATE INDEX idx_rezervare_solicitant      ON rezervare(solicitant_id);
CREATE INDEX idx_rezervare_grant           ON rezervare(grant_id);
CREATE INDEX idx_rezervare_stare_start     ON rezervare(stare, start);
CREATE INDEX idx_aprobare_aprobator        ON aprobare(aprobator_id);
CREATE INDEX idx_notificare_utilizator     ON notificare(utilizator_id, citita);

-- ---------------------------------------------------------------------
-- Soldul de ore al fiecărui grant (atributul derivat /OreConsumate)
-- Orele cererilor în așteptare sunt rezervate din sold, ca să nu se depășească cota.
-- ---------------------------------------------------------------------
CREATE VIEW v_sold_grant AS
SELECT g.id,
       g.cod,
       g.ore_alocate,
       COALESCE(SUM(r.ore_solicitate) FILTER (
           WHERE r.stare IN ('InAsteptareCoordonator', 'InAsteptareLaborator', 'Aprobata', 'Activa', 'Finalizata')
       ), 0)                                                         AS ore_consumate,
       g.ore_alocate - COALESCE(SUM(r.ore_solicitate) FILTER (
           WHERE r.stare IN ('InAsteptareCoordonator', 'InAsteptareLaborator', 'Aprobata', 'Activa', 'Finalizata')
       ), 0)                                                         AS ore_ramase
FROM grant_cercetare g
LEFT JOIN rezervare r ON r.grant_id = g.id
GROUP BY g.id, g.cod, g.ore_alocate;
