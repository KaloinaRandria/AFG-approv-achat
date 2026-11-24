-- Devise des demandes et des bons de commande (étape 1 : affichage, pas de conversion
-- sauf la valorisation du stock en ariary à la réception).
--
-- Chaque document porte sa devise, le cours de cette devise en ariary au moment où elle
-- a été choisie (taux_change) et la date de ce cours. Les documents existants sont en ariary.
-- Le code traite encore une devise absente comme l'ariary : les colonnes restent facultatives.
--
-- Idempotent : peut être relancé sans risque (Hibernate crée aussi les colonnes au démarrage
-- avec ddl-auto=update ; ce script les remplit).

ALTER TABLE demande_mere      ADD COLUMN IF NOT EXISTS id_devise   INTEGER REFERENCES devise (id_devise);
ALTER TABLE demande_mere      ADD COLUMN IF NOT EXISTS taux_change DOUBLE PRECISION;
ALTER TABLE demande_mere      ADD COLUMN IF NOT EXISTS date_taux   TIMESTAMP;

ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_devise   INTEGER REFERENCES devise (id_devise);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS taux_change DOUBLE PRECISION;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_taux   TIMESTAMP;

-- Documents existants : ariary, taux 1, daté de leur création
UPDATE demande_mere
SET id_devise   = (SELECT id_devise FROM devise WHERE UPPER(acronyme) = 'MGA'),
    taux_change = 1,
    date_taux   = COALESCE(date_demande, NOW())
WHERE id_devise IS NULL;

UPDATE bon_commande_mere
SET id_devise   = (SELECT id_devise FROM devise WHERE UPPER(acronyme) = 'MGA'),
    taux_change = 1,
    date_taux   = COALESCE(date_creation, NOW())
WHERE id_devise IS NULL;

-- Contrôle : répartition par devise
SELECT 'demande_mere' AS table_doc, d.acronyme, COUNT(*) FROM demande_mere x LEFT JOIN devise d ON d.id_devise = x.id_devise GROUP BY d.acronyme
UNION ALL
SELECT 'bon_commande_mere', d.acronyme, COUNT(*) FROM bon_commande_mere x LEFT JOIN devise d ON d.id_devise = x.id_devise GROUP BY d.acronyme;
