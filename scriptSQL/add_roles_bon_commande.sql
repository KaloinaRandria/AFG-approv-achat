-- Rôles du bon de commande créé manuellement :
--   MG_CREATOR   : saisit le bon de commande et le soumet à validation
--   MG_VALIDATOR : validation des Moyens Généraux
-- La validation finale est faite par le rôle SG, qui existe déjà.
INSERT INTO role (role)
SELECT 'MG_CREATOR'
WHERE NOT EXISTS (
    SELECT 1 FROM role WHERE role = 'MG_CREATOR'
);

INSERT INTO role (role)
SELECT 'MG_VALIDATOR'
WHERE NOT EXISTS (
    SELECT 1 FROM role WHERE role = 'MG_VALIDATOR'
);
