-- TO-30: e-mailul utilizatorului se păstrează normalizat (fără spații la capete, cu litere mici).
-- Astfel UNIQUE (email) din V1 devine o unicitate fără diferență între litere mari și mici:
-- „Ana@usv.ro” și „ana@usv.ro” nu mai pot exista simultan, iar login-ul caută după valoarea exactă,
-- folosind indexul unic existent.

-- Dacă există deja două adrese care diferă doar prin litere mari/mici, actualizarea eșuează pe
-- UNIQUE (email) și duplicatul trebuie rezolvat manual înainte de migrare.
UPDATE utilizator SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

ALTER TABLE utilizator
    ADD CONSTRAINT utilizator_email_normalizat CHECK (email = lower(btrim(email)));
