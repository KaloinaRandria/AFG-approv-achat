-- Rôles maker / checker génériques : MAKER et CHECKER remplacent IT_MAKER, IT_CHECKER, MG_MAKER, MG_CHECKER.
-- Le domaine vient du rôle métier déjà porté par l'utilisateur :
--    IT + MAKER               : soumet les créations / modifications / (dés)activations de comptes
--    IT + CHECKER             : valide ou rejette ces demandes (une autre personne que l'auteur)
--    MOYENS_GENERAUX + CHECKER : valide les bons de commande (étape M.G.), décide au CODEP, transmet à la finance
--    MOYENS_GENERAUX (+ MAKER) : saisit les bons de commande manuels
-- À exécuter AU MOMENT du déploiement de la version qui utilise ces noms
-- (avant : l'ancienne version perd les droits maker/checker ; après : la nouvelle ne les voit pas).
-- Prérequis vérifié le 2026-11-25 : chaque détenteur de IT_* a IT, chaque détenteur de MG_* a MOYENS_GENERAUX.

BEGIN;

-- 1. Nouveaux rôles
INSERT INTO role (role) SELECT 'MAKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'MAKER');
INSERT INTO role (role) SELECT 'CHECKER'
WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'CHECKER');

-- 2. Report des attributions existantes
INSERT INTO utilisateur_role (id_utilisateur, id_role)
SELECT DISTINCT ur.id_utilisateur, (SELECT id_role FROM role WHERE role = 'MAKER')
FROM utilisateur_role ur JOIN role r ON r.id_role = ur.id_role
WHERE r.role IN ('IT_MAKER', 'MG_MAKER')
  AND NOT EXISTS (SELECT 1 FROM utilisateur_role x JOIN role rx ON rx.id_role = x.id_role
                  WHERE x.id_utilisateur = ur.id_utilisateur AND rx.role = 'MAKER');

INSERT INTO utilisateur_role (id_utilisateur, id_role)
SELECT DISTINCT ur.id_utilisateur, (SELECT id_role FROM role WHERE role = 'CHECKER')
FROM utilisateur_role ur JOIN role r ON r.id_role = ur.id_role
WHERE r.role IN ('IT_CHECKER', 'MG_CHECKER')
  AND NOT EXISTS (SELECT 1 FROM utilisateur_role x JOIN role rx ON rx.id_role = x.id_role
                  WHERE x.id_utilisateur = ur.id_utilisateur AND rx.role = 'CHECKER');

-- 3. Suppression des anciens rôles préfixés
DELETE FROM utilisateur_role
WHERE id_role IN (SELECT id_role FROM role WHERE role IN ('IT_MAKER', 'IT_CHECKER', 'MG_MAKER', 'MG_CHECKER'));
DELETE FROM role WHERE role IN ('IT_MAKER', 'IT_CHECKER', 'MG_MAKER', 'MG_CHECKER');

COMMIT;
