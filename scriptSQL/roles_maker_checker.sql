-- Habilitations maker / checker : <DOMAINE>_MAKER saisit et soumet, <DOMAINE>_CHECKER valide.
-- À exécuter AVANT de déployer la version qui utilise ces noms : sinon les utilisateurs
-- MG_CREATOR / MG_VALIDATOR perdent l'accès aux bons de commande.

-- 1. Moyens Généraux : renommage des rôles existants (les attributions aux utilisateurs sont conservées)
UPDATE role SET role = 'MG_MAKER'   WHERE role = 'MG_CREATOR'
    AND NOT EXISTS (SELECT 1 FROM role WHERE role = 'MG_MAKER');
UPDATE role SET role = 'MG_CHECKER' WHERE role = 'MG_VALIDATOR'
    AND NOT EXISTS (SELECT 1 FROM role WHERE role = 'MG_CHECKER');

INSERT INTO role (role) SELECT 'MG_MAKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'MG_MAKER');
INSERT INTO role (role) SELECT 'MG_CHECKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'MG_CHECKER');

-- 2. Informatique : gestion des utilisateurs
--    IT         : consultation des utilisateurs
--    IT_MAKER   : soumet les créations, modifications et (dés)activations de comptes
--    IT_CHECKER : valide ou rejette ces demandes (une autre personne que l'auteur)
INSERT INTO role (role) SELECT 'IT_MAKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'IT_MAKER');
INSERT INTO role (role) SELECT 'IT_CHECKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'IT_CHECKER');

-- 3. Demandes de compte (créée aussi automatiquement par Hibernate, ddl-auto=update)
CREATE TABLE IF NOT EXISTS demande_compte (
    id_demande_compte    SERIAL PRIMARY KEY,
    type_demande         VARCHAR(20)  NOT NULL,
    statut               VARCHAR(20)  NOT NULL,
    id_utilisateur_cible INTEGER      REFERENCES utilisateur (id_utilisateur),
    libelle_cible        VARCHAR(255),
    donnees              TEXT,
    id_demandeur         INTEGER      NOT NULL REFERENCES utilisateur (id_utilisateur),
    date_demande         TIMESTAMP    NOT NULL,
    id_decideur          INTEGER      REFERENCES utilisateur (id_utilisateur),
    date_decision        TIMESTAMP,
    motif_rejet          TEXT
);
CREATE INDEX IF NOT EXISTS idx_demande_compte_statut ON demande_compte (statut);
