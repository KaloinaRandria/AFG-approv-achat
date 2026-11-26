-- Missions et per diem, lots 0 et 1 (docs/plan-per-diem.md) :
--   - rôle RH (RH seul : consultation ; RH + MAKER : saisie ; RH + CHECKER : DRH, validation) ;
--   - référentiels du barème : catégories de personnel, zones de mission ;
--   - barème des per diem : un montant PAR JOUR (repas + déplacements locaux ; l'hébergement
--     est payé par la banque) par catégorie et par zone, versionné par date d'effet,
--     en circuit maker / checker.
--
-- À exécuter AVANT de démarrer la version de l'appli qui contient les entités
-- CategoriePersonnel, ZoneMission et BaremePerDiem (ddl-auto=validate). Le barème initial
-- est chargé ensuite par 09_per_diem_bareme_initial.sql.
-- Rejouable : ne crée que ce qui manque. Aucune donnée existante n'est modifiée.
-- Les droits du login applicatif suivent par les privilèges par défaut (05_login_applicatif.sql).
--
-- PREPROD : une première version de ce script (avec une colonne type_indemnite) y a été exécutée
-- le 26/11/2026 ; elle est corrigée par scriptSQL/per_diem_correction_preprod.sql.

BEGIN;

-- 1. Rôle RH ----------------------------------------------------------------
INSERT INTO role (role) SELECT 'RH'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'RH');

-- 2. Catégories de personnel ---------------------------------------------------
CREATE TABLE IF NOT EXISTS categorie_personnel (
    id_categorie_personnel SERIAL PRIMARY KEY,
    code                   VARCHAR(50)  NOT NULL UNIQUE,
    libelle                VARCHAR(255) NOT NULL,
    ordre                  INTEGER,
    actif                  BOOLEAN DEFAULT TRUE
);

-- 3. Zones de mission ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS zone_mission (
    id_zone_mission SERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,
    libelle         VARCHAR(255) NOT NULL,
    etranger        BOOLEAN DEFAULT FALSE,
    ordre           INTEGER,
    actif           BOOLEAN DEFAULT TRUE
);

-- 4. Barème des per diem (montant par jour) ------------------------------------
-- Validité : date_effet <= jour < date_fin (date_fin exclue, NULL = en vigueur).
CREATE TABLE IF NOT EXISTS bareme_per_diem (
    id_bareme_per_diem     SERIAL PRIMARY KEY,
    id_categorie_personnel INTEGER        NOT NULL REFERENCES categorie_personnel (id_categorie_personnel),
    id_zone_mission        INTEGER        NOT NULL REFERENCES zone_mission (id_zone_mission),
    montant                NUMERIC(15, 2) NOT NULL CHECK (montant > 0),
    id_devise              INTEGER        NOT NULL REFERENCES devise (id_devise),
    date_effet             DATE           NOT NULL,
    date_fin               DATE,
    statut                 VARCHAR(20)    NOT NULL
        CHECK (statut IN ('PROPOSE', 'VALIDE', 'REJETE', 'ANNULE')),
    id_propose_par         INTEGER REFERENCES referentiel.utilisateur (id_utilisateur),
    date_proposition       TIMESTAMP,
    motif                  TEXT,
    id_traite_par          INTEGER REFERENCES referentiel.utilisateur (id_utilisateur),
    date_traitement        TIMESTAMP,
    commentaire_traitement TEXT,
    CONSTRAINT ck_bareme_periode CHECK (date_fin IS NULL OR date_fin > date_effet)
);

-- Une seule proposition en attente par case du barème (catégorie, zone)
CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_propose
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission)
    WHERE statut = 'PROPOSE';

-- Deux taux validés d'une même case n'ont pas la même date d'effet
CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_valide
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission, date_effet)
    WHERE statut = 'VALIDE';

-- Au plus un taux validé ouvert (sans date de fin) par case
CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_ouvert
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission)
    WHERE statut = 'VALIDE' AND date_fin IS NULL;

-- Recherche du taux applicable à une mission
CREATE INDEX IF NOT EXISTS ix_bareme_per_diem_recherche
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission, statut, date_effet);

COMMIT;

-- Contrôles
SELECT 'role RH' AS objet, COUNT(*) AS nb FROM role WHERE role = 'RH'
UNION ALL SELECT 'categorie_personnel', COUNT(*) FROM categorie_personnel
UNION ALL SELECT 'zone_mission', COUNT(*) FROM zone_mission
UNION ALL SELECT 'bareme_per_diem', COUNT(*) FROM bareme_per_diem;
