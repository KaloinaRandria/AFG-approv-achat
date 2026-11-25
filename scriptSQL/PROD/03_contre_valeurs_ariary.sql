-- ════════════════════════════════════════════════════════════════════════════
-- Contre-valeurs en ariary enregistrées sur les documents
--
-- Les montants restent dans la devise du document (proforma, bon de commande, facture) ;
-- on enregistre en plus leur contre-valeur en ariary, au cours figé du document :
--   demande_mere      : total_estime_ariary, total_prix_ariary
--   bon_commande_mere : montant_ht_ariary, montant_ttc_ariary
--   bon_livraison_mere: total_prix_ariary (cours du bon de commande réceptionné,
--                       sinon cours actuel de la devise du BL, 1 pour l'ariary)
-- L'application les recalcule à chaque enregistrement ; ce script remplit les documents
-- existants (sans écraser une valeur déjà calculée).
--
-- Prérequis : scripts 01 et 02. Rejouable.
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

ALTER TABLE demande_mere       ADD COLUMN IF NOT EXISTS total_estime_ariary DOUBLE PRECISION;
ALTER TABLE demande_mere       ADD COLUMN IF NOT EXISTS total_prix_ariary   DOUBLE PRECISION;
ALTER TABLE bon_commande_mere  ADD COLUMN IF NOT EXISTS montant_ht_ariary   DOUBLE PRECISION;
ALTER TABLE bon_commande_mere  ADD COLUMN IF NOT EXISTS montant_ttc_ariary  DOUBLE PRECISION;
ALTER TABLE bon_livraison_mere ADD COLUMN IF NOT EXISTS total_prix_ariary   DOUBLE PRECISION;

-- Cours effectif d'un document : taux_change, ou 1 s'il manque ou n'est pas valable (documents en ariary)
UPDATE demande_mere
   SET total_estime_ariary = ROUND((total_estime * CASE WHEN taux_change > 0 THEN taux_change ELSE 1 END)::numeric, 2)
 WHERE total_estime IS NOT NULL AND total_estime_ariary IS NULL;

UPDATE demande_mere
   SET total_prix_ariary = ROUND((total_prix * CASE WHEN taux_change > 0 THEN taux_change ELSE 1 END)::numeric, 2)
 WHERE total_prix IS NOT NULL AND total_prix_ariary IS NULL;

UPDATE bon_commande_mere
   SET montant_ht_ariary  = ROUND((montant_ht  * CASE WHEN taux_change > 0 THEN taux_change ELSE 1 END)::numeric, 2),
       montant_ttc_ariary = ROUND((montant_ttc * CASE WHEN taux_change > 0 THEN taux_change ELSE 1 END)::numeric, 2)
 WHERE montant_ttc_ariary IS NULL;

UPDATE bon_livraison_mere bl
   SET total_prix_ariary = ROUND((bl.total_prix * CASE
           WHEN bc.id_bc_mere IS NOT NULL THEN CASE WHEN bc.taux_change > 0 THEN bc.taux_change ELSE 1 END
           WHEN d.id_devise IS NULL OR UPPER(d.acronyme) = 'MGA' OR d.cours_ariary <= 0 THEN 1
           ELSE d.cours_ariary
       END)::numeric, 2)
  FROM bon_livraison_mere x
  LEFT JOIN bon_commande_mere bc ON bc.id_bc_mere = x.id_bc_mere
  LEFT JOIN devise d ON d.id_devise = x.id_devise
 WHERE x.id_bl_mere = bl.id_bl_mere
   AND bl.total_prix IS NOT NULL AND bl.total_prix_ariary IS NULL;

-- Contrôle : documents dont le montant est renseigné sans contre-valeur (attendu : 0 partout)
SELECT 'demande_mere' AS table_doc, COUNT(*) AS sans_contre_valeur
  FROM demande_mere WHERE (total_estime IS NOT NULL AND total_estime_ariary IS NULL)
                       OR (total_prix IS NOT NULL AND total_prix_ariary IS NULL)
UNION ALL
SELECT 'bon_commande_mere', COUNT(*) FROM bon_commande_mere WHERE montant_ttc_ariary IS NULL
UNION ALL
SELECT 'bon_livraison_mere', COUNT(*) FROM bon_livraison_mere WHERE total_prix IS NOT NULL AND total_prix_ariary IS NULL;

COMMIT;
