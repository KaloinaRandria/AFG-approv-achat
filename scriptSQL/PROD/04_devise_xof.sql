-- ════════════════════════════════════════════════════════════════════════════
-- 04 : ajout de la devise XOF (franc CFA BCEAO, Afrique de l'Ouest)
--
-- Le XOF est arrimé à l'euro par une parité fixe : 1 EUR = 655,957 XOF.
-- Son cours en ariary est donc déduit du cours EUR enregistré :
--     1 XOF = cours EUR / 655,957 MGA
-- (ex. EUR à 5 029,58 MGA  →  1 XOF ≈ 7,6676 MGA).
-- Sans devise EUR, le cours est mis à 0 et doit être saisi dans l'application.
--
-- Rejouable : n'insère rien si le XOF existe déjà.
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

INSERT INTO devise (acronyme, designation, cours_ariary, date_mise_a_jour)
SELECT 'XOF',
       'Franc CFA (BCEAO)',
       COALESCE((SELECT ROUND((cours_ariary / 655.957)::numeric, 4)
                 FROM devise
                 WHERE UPPER(acronyme) = 'EUR' AND cours_ariary > 0), 0),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM devise WHERE UPPER(acronyme) = 'XOF');

COMMIT;

-- Contrôle
SELECT id_devise, acronyme, designation, cours_ariary, date_mise_a_jour
FROM devise
WHERE UPPER(acronyme) IN ('EUR', 'XOF')
ORDER BY acronyme;
