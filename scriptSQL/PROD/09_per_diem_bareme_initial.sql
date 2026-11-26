-- Barème initial des per diem (grille communiquée par la DRH le 26/11/2026).
--
-- Per diem = montant PAR JOUR de mission : repas + déplacements locaux. L'hébergement est payé
-- directement par la banque.
--
--                                         Madagascar   Afrique      Hors Afrique (*)
--   DG / DGA / SG                         180 000 MGA  90 000 XOF   110 000 XOF
--   Cadre supérieur (CODIR)               150 000 MGA  75 000 XOF    90 000 XOF
--   Cadre moyen (chef de service / dép.)  150 000 MGA  60 000 XOF    80 000 XOF
--   Non cadre                             110 000 MGA  50 000 XOF    75 000 XOF
--
--   (*) y compris Afrique du Nord, océan Indien et Afrique du Sud.
--   À Madagascar, la grille ne connaît que « Cadres » : même taux pour CODIR et cadre moyen.
--   Étranger : enregistré en franc CFA (montants ronds) ; l'appli affiche la contre-valeur en euro
--   (90 000 XOF ≈ 137,20 €, parité fixe 655,957).
--
-- Taux enregistrés directement VALIDÉS (reprise de la grille en vigueur), date d'effet 01/01/2026.
-- Prérequis : 04_devise_xof.sql (devise XOF) et 08_per_diem_bareme.sql.
-- Rejouable : catégories et zones mises à jour par leur code ; un taux n'est créé que si la case
-- n'a encore aucun taux validé.

BEGIN;

-- 1. Catégories de personnel (lignes de la grille)
INSERT INTO categorie_personnel (code, libelle, ordre, actif) VALUES
    ('DG_DGA_SG',             'DG / DGA / SG',                                    10, TRUE),
    ('CADRE_SUPERIEUR_CODIR', 'Cadre supérieur (CODIR)',                          20, TRUE),
    ('CADRE_MOYEN',           'Cadre moyen (chef de service ou de département)',  30, TRUE),
    ('NON_CADRE',             'Non cadre',                                        40, TRUE)
ON CONFLICT (code) DO UPDATE SET libelle = EXCLUDED.libelle, ordre = EXCLUDED.ordre, actif = TRUE;

-- 2. Zones de mission (colonnes de la grille)
INSERT INTO zone_mission (code, libelle, etranger, ordre, actif) VALUES
    ('MADAGASCAR',   'Madagascar',                                                       FALSE, 10, TRUE),
    ('AFRIQUE',      'Afrique',                                                          TRUE,  20, TRUE),
    ('HORS_AFRIQUE', 'Hors Afrique (y c. Afrique du Nord, océan Indien, Afrique du Sud)', TRUE,  30, TRUE)
ON CONFLICT (code) DO UPDATE SET libelle = EXCLUDED.libelle, etranger = EXCLUDED.etranger,
                                 ordre = EXCLUDED.ordre, actif = TRUE;

-- 3. Taux journaliers
INSERT INTO bareme_per_diem (id_categorie_personnel, id_zone_mission, montant, id_devise,
                             date_effet, statut, date_proposition, motif, date_traitement)
SELECT c.id_categorie_personnel, z.id_zone_mission, g.montant, d.id_devise,
       DATE '2026-01-01', 'VALIDE', now(), 'Barème initial (grille DRH du 26/11/2026)', now()
FROM (VALUES
        ('DG_DGA_SG',             'MADAGASCAR',   180000, 'MGA'),
        ('CADRE_SUPERIEUR_CODIR', 'MADAGASCAR',   150000, 'MGA'),
        ('CADRE_MOYEN',           'MADAGASCAR',   150000, 'MGA'),
        ('NON_CADRE',             'MADAGASCAR',   110000, 'MGA'),
        ('DG_DGA_SG',             'AFRIQUE',       90000, 'XOF'),
        ('CADRE_SUPERIEUR_CODIR', 'AFRIQUE',       75000, 'XOF'),
        ('CADRE_MOYEN',           'AFRIQUE',       60000, 'XOF'),
        ('NON_CADRE',             'AFRIQUE',       50000, 'XOF'),
        ('DG_DGA_SG',             'HORS_AFRIQUE', 110000, 'XOF'),
        ('CADRE_SUPERIEUR_CODIR', 'HORS_AFRIQUE',  90000, 'XOF'),
        ('CADRE_MOYEN',           'HORS_AFRIQUE',  80000, 'XOF'),
        ('NON_CADRE',             'HORS_AFRIQUE',  75000, 'XOF')
     ) AS g (code_categorie, code_zone, montant, devise)
JOIN categorie_personnel c ON c.code = g.code_categorie
JOIN zone_mission z        ON z.code = g.code_zone
JOIN devise d              ON d.acronyme = g.devise
WHERE NOT EXISTS (SELECT 1 FROM bareme_per_diem b
                  WHERE b.id_categorie_personnel = c.id_categorie_personnel
                    AND b.id_zone_mission = z.id_zone_mission
                    AND b.statut = 'VALIDE');

-- La grille doit être complète : 12 taux validés (échec si la devise XOF manque, par exemple)
DO $$
DECLARE n INTEGER;
BEGIN
    SELECT COUNT(*) INTO n
    FROM bareme_per_diem b
    JOIN categorie_personnel c ON c.id_categorie_personnel = b.id_categorie_personnel
    JOIN zone_mission z        ON z.id_zone_mission = b.id_zone_mission
    WHERE b.statut = 'VALIDE'
      AND c.code IN ('DG_DGA_SG', 'CADRE_SUPERIEUR_CODIR', 'CADRE_MOYEN', 'NON_CADRE')
      AND z.code IN ('MADAGASCAR', 'AFRIQUE', 'HORS_AFRIQUE');
    IF n < 12 THEN
        RAISE EXCEPTION 'Barème incomplet : % taux validés sur 12 (devise MGA ou XOF absente ?)', n;
    END IF;
END $$;

COMMIT;

-- Contrôle : la grille
SELECT c.libelle AS categorie, z.libelle AS zone, b.montant, d.acronyme AS devise, b.date_effet
FROM bareme_per_diem b
JOIN categorie_personnel c ON c.id_categorie_personnel = b.id_categorie_personnel
JOIN zone_mission z        ON z.id_zone_mission = b.id_zone_mission
JOIN devise d              ON d.id_devise = b.id_devise
WHERE b.statut = 'VALIDE' AND b.date_fin IS NULL
ORDER BY c.ordre, z.ordre;
