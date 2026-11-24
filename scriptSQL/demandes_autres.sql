-- Demandes Autres : une seule dépense hors matériel (sponsoring, remboursement, indemnité de repas,
-- lot / récompense, droits divers / honoraires…), réglée en paiement direct, sans bon de commande.
-- Circuit complet : N+1 → Moyens Généraux → contrôleur de gestion → D.F.C. → S.G.
--
-- Le type de demande impose désormais le mode de traitement :
--   matériel (FOURNITURE) → « En attente Moyens Généraux » (bon de commande)
--   autre    (AUTRE)      → « Paiement direct » (sans bon de commande)
-- Le code retrouve ces modes par leur libellé : ils doivent exister dans mode_traitement.
--
-- Idempotent : peut être relancé sans risque (Hibernate crée aussi les tables au démarrage
-- avec ddl-auto=update ; ce script les crée à l'identique et les remplit).

-- ── Nouveau type AUTRE ──────────────────────────────────────────────────────
-- Hibernate a créé une contrainte CHECK sur l'enum (FOURNITURE, STANDARD) et ddl-auto=update
-- ne la met jamais à jour : sans cette correction, l'insertion d'une demande AUTRE échoue.
ALTER TABLE demande_mere DROP CONSTRAINT IF EXISTS demande_mere_type_demande_check;
ALTER TABLE demande_mere ADD CONSTRAINT demande_mere_type_demande_check
    CHECK (type_demande IN ('FOURNITURE', 'AUTRE', 'STANDARD'));

-- ── Référentiel des catégories ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS categorie_depense (
    id_categorie_depense      SERIAL PRIMARY KEY,
    code                      VARCHAR(50)  NOT NULL UNIQUE,
    libelle                   VARCHAR(255) NOT NULL,
    beneficiaire_obligatoire  BOOLEAN DEFAULT TRUE,
    actif                     BOOLEAN DEFAULT TRUE,
    ordre                     INTEGER
);

INSERT INTO categorie_depense (code, libelle, beneficiaire_obligatoire, actif, ordre) VALUES
    ('SPONSORING',       'Sponsoring',                    TRUE, TRUE, 10),
    ('REMBOURSEMENT',    'Remboursement',                 TRUE, TRUE, 20),
    ('INDEMNITE_REPAS',  'Indemnité de repas',            TRUE, TRUE, 30),
    ('LOT_RECOMPENSE',   'Lot / Récompense',              TRUE, TRUE, 40),
    ('DROITS_HONORAIRES','Droits (divers / honoraires)',  TRUE, TRUE, 50)
ON CONFLICT (code) DO NOTHING;

-- ── Dépense d'une demande Autre (une seule par demande) ────────────────────
CREATE TABLE IF NOT EXISTS demande_autre_detail (
    id_demande_autre_detail  SERIAL PRIMARY KEY,
    id_demande_mere          VARCHAR(255) NOT NULL UNIQUE REFERENCES demande_mere (id_demande_mere),
    id_categorie_depense     INTEGER      NOT NULL REFERENCES categorie_depense (id_categorie_depense),
    beneficiaire             VARCHAR(255),
    quantite                 DOUBLE PRECISION,
    montant_unitaire         DOUBLE PRECISION,
    montant                  DOUBLE PRECISION
);

-- ── Modes de traitement imposés par le type de demande ─────────────────────
INSERT INTO mode_traitement (libelle, necessite_bon_commande)
SELECT 'Paiement direct', FALSE
WHERE NOT EXISTS (SELECT 1 FROM mode_traitement WHERE LOWER(libelle) = LOWER('Paiement direct'));

INSERT INTO mode_traitement (libelle, necessite_bon_commande)
SELECT 'En attente Moyens Généraux', TRUE
WHERE NOT EXISTS (SELECT 1 FROM mode_traitement WHERE LOWER(libelle) = LOWER('En attente Moyens Généraux'));

-- Demandes de matériel en cours sans mode : le type l'impose
UPDATE demande_mere
SET id_mode_traitement = (SELECT id_mode_traitement FROM mode_traitement
                          WHERE LOWER(libelle) = LOWER('En attente Moyens Généraux') LIMIT 1)
WHERE type_demande = 'FOURNITURE' AND id_mode_traitement IS NULL;

-- Contrôles
SELECT * FROM categorie_depense ORDER BY ordre;
SELECT id_mode_traitement, libelle, necessite_bon_commande FROM mode_traitement ORDER BY id_mode_traitement;
