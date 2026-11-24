-- Rattachement des postes aux directions (table service).
--
-- Chaque poste peut être rattaché à une direction (colonne facultative id_service).
-- Hibernate crée aussi la colonne au démarrage (ddl-auto=update) ; ce script la crée
-- à l'identique.
--
-- Les rattachements se font depuis l'écran Paramétrage des opérations > Directions et postes :
-- la direction des utilisateurs contient des erreurs (ex. Agent Comptable en DRH), un
-- pré-remplissage automatique les recopierait dans les postes. L'écran signale ensuite
-- les utilisateurs dont la direction ne correspond pas à celle de leur poste.
--
-- Idempotent : peut être relancé sans risque.

ALTER TABLE poste ADD COLUMN IF NOT EXISTS id_service INTEGER REFERENCES service (id_service);

-- FACULTATIF, à ne lancer qu'après avoir corrigé la direction des utilisateurs :
-- rattache un poste à la direction de ses utilisateurs quand ils sont TOUS dans la même.
/*
UPDATE poste p
SET id_service = x.id_service
FROM (
    SELECT u.id_poste, MIN(u.id_service) AS id_service
    FROM utilisateur u
    WHERE u.id_poste IS NOT NULL AND u.id_service IS NOT NULL
    GROUP BY u.id_poste
    HAVING COUNT(DISTINCT u.id_service) = 1
) x
WHERE p.id_poste = x.id_poste
  AND p.id_service IS NULL;
*/

-- Contrôle : postes rattachés / non rattachés
SELECT CASE WHEN id_service IS NULL THEN 'non rattachés' ELSE 'rattachés' END AS etat, COUNT(*)
FROM poste
GROUP BY 1;

-- Postes restant à rattacher, avec les directions de leurs utilisateurs
SELECT TRIM(p.poste) AS poste, STRING_AGG(DISTINCT s.acronyme, ', ') AS directions_des_utilisateurs
FROM poste p
LEFT JOIN utilisateur u ON u.id_poste = p.id_poste
LEFT JOIN service s ON s.id_service = u.id_service
WHERE p.id_service IS NULL
GROUP BY p.id_poste, p.poste
ORDER BY 1;
