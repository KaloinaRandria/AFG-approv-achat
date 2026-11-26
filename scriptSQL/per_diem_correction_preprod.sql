-- PREPROD UNIQUEMENT : corrige la première version de PROD/08_per_diem_bareme.sql exécutée le 26/11/2026.
--
-- Le per diem est un montant unique par jour (repas + déplacements locaux ; l'hébergement est payé
-- par la banque) : la colonne type_indemnite disparaît. La supprimer supprime aussi les index qui
-- l'utilisaient ; ils sont recréés sur (catégorie, zone), comme dans la version corrigée du 08.
--
-- La catégorie de test « Cadre » (code CADRE) est désactivée, sans être supprimée : le barème
-- distingue « Cadre supérieur (CODIR) » et « Cadre moyen » (créées par 09_per_diem_bareme_initial.sql).
--
-- Ordre : ce script, puis PROD/09_per_diem_bareme_initial.sql. Ne pas exécuter en PROD.
-- Rejouable. S'arrête sans rien modifier si le barème contient déjà des taux.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'bareme_per_diem'
                 AND column_name = 'type_indemnite')
       AND EXISTS (SELECT 1 FROM bareme_per_diem) THEN
        RAISE EXCEPTION 'bareme_per_diem contient déjà des taux : correction à revoir avant suppression de type_indemnite';
    END IF;
END $$;

ALTER TABLE bareme_per_diem DROP COLUMN IF EXISTS type_indemnite;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_propose
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission)
    WHERE statut = 'PROPOSE';

CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_valide
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission, date_effet)
    WHERE statut = 'VALIDE';

CREATE UNIQUE INDEX IF NOT EXISTS ux_bareme_per_diem_ouvert
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission)
    WHERE statut = 'VALIDE' AND date_fin IS NULL;

CREATE INDEX IF NOT EXISTS ix_bareme_per_diem_recherche
    ON bareme_per_diem (id_categorie_personnel, id_zone_mission, statut, date_effet);

UPDATE categorie_personnel SET actif = FALSE WHERE code = 'CADRE';

COMMIT;

-- Contrôles : plus de colonne type_indemnite, 5 index (clé primaire + 4)
SELECT 'colonne type_indemnite' AS objet, COUNT(*) AS nb FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'bareme_per_diem' AND column_name = 'type_indemnite'
UNION ALL SELECT 'index bareme_per_diem', COUNT(*) FROM pg_indexes WHERE tablename = 'bareme_per_diem';
