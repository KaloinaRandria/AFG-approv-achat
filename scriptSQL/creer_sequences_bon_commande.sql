-- Objets nécessaires à la création des bons de commande, absents de la base PREPROD :
--   s_bc_mere   : séquence des identifiants (BC00000001, BC00000002…)
--   compteur_bc : compteur annuel des numéros de bon de commande (JJMMAAAA-n)
-- Ni l'un ni l'autre n'est créé par Hibernate : ils viennent de achat-approv-script.sql.
--
-- Script rejouable : il ne crée que ce qui manque et ne fait jamais reculer la séquence.

BEGIN;

-- 1. Séquence des identifiants de bon de commande
CREATE SEQUENCE IF NOT EXISTS s_bc_mere
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Si des bons de commande existent déjà, la séquence reprend après le plus grand identifiant
-- (évite une violation de clé primaire sur id_bc_mere). Sans bon existant, rien ne change.
SELECT setval('s_bc_mere', x.max_id)
FROM (SELECT MAX(CAST(SUBSTRING(id_bc_mere FROM 3) AS BIGINT)) AS max_id
      FROM bon_commande_mere
      WHERE id_bc_mere ~ '^BC[0-9]+$') AS x
WHERE x.max_id IS NOT NULL
  AND x.max_id >= (SELECT last_value FROM s_bc_mere);

-- 2. Compteur annuel des numéros de bon de commande
CREATE TABLE IF NOT EXISTS compteur_bc (
    annee          INTEGER PRIMARY KEY,
    dernier_numero INTEGER NOT NULL
);

-- 3. Contrôle : les objets utilisés par la génération des identifiants doivent tous exister
SELECT nom, CASE WHEN existe THEN 'OK' ELSE 'MANQUANT' END AS etat
FROM (VALUES
    ('séquence s_code_article (articles)',                          to_regclass('s_code_article') IS NOT NULL),
    ('séquence demande_mere_id_demande_mere_seq (demandes)',        to_regclass('demande_mere_id_demande_mere_seq') IS NOT NULL),
    ('séquence bon_livraison_mere_id_bl_mere_seq (bons de livraison)', to_regclass('bon_livraison_mere_id_bl_mere_seq') IS NOT NULL),
    ('séquence s_bc_mere (bons de commande)',                       to_regclass('s_bc_mere') IS NOT NULL),
    ('table compteur_bc (numéros de bon de commande)',              to_regclass('compteur_bc') IS NOT NULL)
) AS objets(nom, existe);

COMMIT;
