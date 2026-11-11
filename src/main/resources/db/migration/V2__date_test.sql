-- =====================================================================
-- Research Resource Manager - date de test
-- Flyway: src/main/resources/db/migration/V2__date_test.sql
-- Parola tuturor utilizatorilor de test: parola123 (hash BCrypt)
-- Datele rezervărilor sunt relative la ziua curentă, ca demo-ul să rămână valabil.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Utilizatori
-- ---------------------------------------------------------------------
INSERT INTO utilizator (id, nume, email, parola_hash, doi_factori_activ) VALUES
 (1, 'Administrator Sistem',      'admin@usv.ro',                   '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', TRUE),
 (2, 'Conf. dr. Mihai Cernei',    'mihai.cernei@usv.ro',            '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', TRUE),
 (3, 'Lect. dr. Ana Rusu',        'ana.rusu@usv.ro',                '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', TRUE),
 (4, 'Prof. dr. Ion Popa',        'ion.popa@usv.ro',                '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', TRUE),
 (5, 'Conf. dr. Elena Marin',     'elena.marin@usv.ro',             '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', FALSE),
 (6, 'Andrei Popescu',            'andrei.popescu@student.usv.ro',  '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', FALSE),
 (7, 'Maria Ionescu',             'maria.ionescu@student.usv.ro',   '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', TRUE),
 (8, 'Ștefan Rusu',               'stefan.rusu@student.usv.ro',     '$2a$10$9FK1Ml7BUov.W3/kWuojt.QMzCEcIlzHjTpJpDn1cszLzf69n0neG', FALSE);

-- ---------------------------------------------------------------------
-- Granturi, laboratoare, roluri cu scop
-- ---------------------------------------------------------------------
INSERT INTO grant_cercetare (id, cod, coordonator_id, ore_alocate, data_start, data_sfarsit) VALUES
 (1, 'PN-III-P4-2026',  4, 120, DATE '2026-01-01', DATE '2027-12-31'),
 (2, 'ERASMUS-DIGILAB', 5,  80, DATE '2026-09-01', DATE '2027-08-31');

INSERT INTO membru_grant (grant_id, utilizator_id, data_aderare) VALUES
 (1, 6, DATE '2026-02-01'),
 (1, 7, DATE '2026-02-01'),
 (2, 7, DATE '2026-09-15'),
 (2, 8, DATE '2026-09-15');

INSERT INTO laborator (id, denumire, locatie, responsabil_id) VALUES
 (1, 'Laborator Robotică',   'Corp C, sala C204', 2),
 (2, 'Laborator Electronică', 'Corp C, sala C201', 3);

INSERT INTO utilizator_rol (utilizator_id, rol, tip_scop, scop_id) VALUES
 (1, 'AdministratorSistem',  'Global',    NULL),
 (2, 'ResponsabilLaborator', 'Laborator', 1),
 (3, 'ResponsabilLaborator', 'Laborator', 2),
 (4, 'CoordonatorGrant',     'Grant',     1),
 (5, 'CoordonatorGrant',     'Grant',     2),
 (6, 'Student',              'Global',    NULL),
 (7, 'Student',              'Global',    NULL),
 (8, 'Student',              'Global',    NULL);

-- ---------------------------------------------------------------------
-- Catalogul de resurse
-- ---------------------------------------------------------------------
INSERT INTO categorie_resursa (id, denumire) VALUES
 (1, 'Robot educațional'),
 (2, 'Echipament'),
 (3, 'Spațiu de laborator'),
 (4, 'Nod de calcul');

INSERT INTO resursa (id, laborator_id, categorie_id, denumire, specificatii, stare, timp_pregatire_min, reguli_acces) VALUES
 (1, 1, 2, 'Braț robotic UR5',        'Universal Robots UR5, 6 axe, sarcină utilă 5 kg, rază 850 mm', 'Activa',     15, 'Necesită instruire SSM'),
 (2, 1, 1, 'Robot educațional NAO',   'SoftBank NAO v6, 25 grade de libertate',                       'Activa',     10, NULL),
 (3, 1, 3, 'Sala C204',               'Laborator cu 16 posturi de lucru',                             'Activa',      0, NULL),
 (4, 2, 2, 'Osciloscop Rigol DS1054', '4 canale, 50 MHz',                                             'Activa',      5, NULL),
 (5, 2, 2, 'Imprimantă 3D Prusa MK4', 'Volum de lucru 250 × 210 × 220 mm',                            'Activa',     30, 'Filamentul se aduce de solicitant'),
 (6, 2, 2, 'Microscop Leica DM750',   'Mărire 40×–1000×',                                             'Mentenanta',  0, NULL);

INSERT INTO atasament (resursa_id, nume_fisier, cale) VALUES
 (1, 'fisa_tehnica_UR5.pdf',        '/uploads/resurse/1/fisa_tehnica_UR5.pdf'),
 (1, 'regulament_utilizare.pdf',    '/uploads/resurse/1/regulament_utilizare.pdf'),
 (5, 'ghid_imprimare_prusa.pdf',    '/uploads/resurse/5/ghid_imprimare_prusa.pdf');

-- ---------------------------------------------------------------------
-- Rezervări în toate stările (sfarsit_blocat este calculat de trigger)
-- ---------------------------------------------------------------------
INSERT INTO rezervare (id, solicitant_id, resursa_id, grant_id, stare, start, sfarsit, ore_solicitate, scop, data_check_in) VALUES
 -- finalizată (în trecut, cu check-in)
 (1, 6, 1, 1, 'Finalizata',
     date_trunc('day', now()) - interval '3 days' + interval '10 hours',
     date_trunc('day', now()) - interval '3 days' + interval '12 hours', 2.0,
     'Calibrarea traiectoriilor pentru grantul PN-III',
     date_trunc('day', now()) - interval '3 days' + interval '10 hours 5 minutes'),
 -- aprobată (mâine)
 (2, 7, 1, 1, 'Aprobata',
     date_trunc('day', now()) + interval '1 day 10 hours',
     date_trunc('day', now()) + interval '1 day 12 hours', 2.0,
     'Testare algoritm de prindere a obiectelor', NULL),
 -- aprobată de coordonator, așteaptă laboratorul
 (3, 6, 5, 1, 'InAsteptareLaborator',
     date_trunc('day', now()) + interval '1 day 14 hours',
     date_trunc('day', now()) + interval '1 day 16 hours', 2.0,
     'Imprimarea carcasei pentru senzorul de proximitate', NULL),
 -- cerere nouă, așteaptă coordonatorul
 (4, 8, 4, 2, 'InAsteptareCoordonator',
     date_trunc('day', now()) + interval '2 days 9 hours',
     date_trunc('day', now()) + interval '2 days 10 hours 30 minutes', 1.5,
     'Măsurători pe placa de achiziție DigiLab', NULL),
 -- respinsă de coordonator
 (5, 7, 2, 2, 'Respinsa',
     date_trunc('day', now()) + interval '2 days 11 hours',
     date_trunc('day', now()) + interval '2 days 13 hours', 2.0,
     'Demonstrație pentru ziua porților deschise', NULL),
 -- anulată automat (fără check-in)
 (6, 8, 1, 2, 'AnulataAutomat',
     date_trunc('day', now()) - interval '1 day' + interval '14 hours',
     date_trunc('day', now()) - interval '1 day' + interval '16 hours', 2.0,
     'Programare traiectorii pick-and-place', NULL),
 -- anulată de student; are același interval ca rezervarea 2, dar nu blochează resursa
 (7, 6, 1, 1, 'Anulata',
     date_trunc('day', now()) + interval '1 day 10 hours',
     date_trunc('day', now()) + interval '1 day 12 hours', 2.0,
     'Test braț robotic (anulat de solicitant)', NULL);

-- ---------------------------------------------------------------------
-- Aprobări (nivel 1 = Coordonator Grant, nivel 2 = Responsabil Laborator)
-- ---------------------------------------------------------------------
INSERT INTO aprobare (rezervare_id, nivel, aprobator_id, decizie, observatie, data_decizie) VALUES
 (1, 1, 4, 'Aprobat', NULL,                                         now() - interval '5 days'),
 (1, 2, 2, 'Aprobat', 'Instruire SSM verificată.',                  now() - interval '5 days' + interval '2 hours'),
 (2, 1, 4, 'Aprobat', NULL,                                         now() - interval '1 day'),
 (2, 2, 2, 'Aprobat', NULL,                                         now() - interval '20 hours'),
 (3, 1, 4, 'Aprobat', 'Încadrat în bugetul de ore.',                now() - interval '3 hours'),
 (5, 1, 5, 'Respins', 'Activitatea nu ține de obiectivele grantului.', now() - interval '2 hours'),
 (6, 1, 5, 'Aprobat', NULL,                                         now() - interval '3 days'),
 (6, 2, 2, 'Aprobat', NULL,                                         now() - interval '3 days' + interval '1 hour');

-- ---------------------------------------------------------------------
-- Notificări
-- ---------------------------------------------------------------------
INSERT INTO notificare (utilizator_id, rezervare_id, tip, mesaj, citita, data_creare) VALUES
 (7, 2,    'SchimbareStare', 'Rezervarea pentru Braț robotic UR5 a fost aprobată.',                 TRUE,  now() - interval '20 hours'),
 (7, 2,    'Memento',        'Rezervarea pentru Braț robotic UR5 începe mâine la 10:00.',           FALSE, now() - interval '1 hour'),
 (6, 3,    'SchimbareStare', 'Cererea pentru Imprimantă 3D Prusa MK4 a fost aprobată de coordonator.', FALSE, now() - interval '3 hours'),
 (2, 3,    'SchimbareStare', 'O cerere nouă așteaptă validarea laboratorului.',                     FALSE, now() - interval '3 hours'),
 (5, 4,    'SchimbareStare', 'O cerere nouă așteaptă aprobarea dumneavoastră.',                     FALSE, now() - interval '30 minutes'),
 (7, 5,    'SchimbareStare', 'Cererea pentru Robot educațional NAO a fost respinsă.',               FALSE, now() - interval '2 hours'),
 (8, 6,    'SchimbareStare', 'Rezervarea a fost anulată automat: check-in-ul nu a fost efectuat.',  TRUE,  now() - interval '1 day'),
 (6, NULL, 'Mentenanta',     'Microscop Leica DM750 este în mentenanță.',                            FALSE, now() - interval '2 days');

-- ---------------------------------------------------------------------
-- Sincronizarea secvențelor după inserarea cu ID-uri explicite
-- ---------------------------------------------------------------------
SELECT setval('utilizator_id_seq',        (SELECT MAX(id) FROM utilizator));
SELECT setval('grant_cercetare_id_seq',   (SELECT MAX(id) FROM grant_cercetare));
SELECT setval('laborator_id_seq',         (SELECT MAX(id) FROM laborator));
SELECT setval('categorie_resursa_id_seq', (SELECT MAX(id) FROM categorie_resursa));
SELECT setval('resursa_id_seq',           (SELECT MAX(id) FROM resursa));
SELECT setval('rezervare_id_seq',         (SELECT MAX(id) FROM rezervare));
