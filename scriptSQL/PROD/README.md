# Scripts SQL à exécuter en PROD

Base cible : **ACHAT_APPROV_PROD**. Ce dossier ne contient que les scripts qui restent à
exécuter en production, dans l'ordre de leur numéro.

## Ordre d'exécution

| N° | Script | Contenu | Exécuté en PREPROD | Exécuté en PROD |
|----|--------|---------|--------------------|-----------------|
| 01 | `01_alignement_schema_preprod.sql` | Alignement du schéma PROD sur la PREPROD au 25/11/2026 (regroupe les anciens scripts de `scriptSQL/`) | oui | **non** |
| 02 | `02_inventaire_stock.sql` | Inventaires du stock : tables, lien `stock_mere` → `inventaire`, vue de l'historique des mouvements | oui (25/11/2026) | **non** |
| 03 | `03_contre_valeurs_ariary.sql` | Contre-valeurs en ariary enregistrées sur les demandes, bons de commande et bons de livraison, avec reprise de l'existant | non | **non** |

## Mode d'emploi

1. Sauvegarder la base PROD (`pg_dump`) et arrêter l'application.
2. Exécuter les scripts **dans l'ordre des numéros**, chacun en entier (psql -f, ou pgAdmin
   en une seule exécution). Chaque script est dans une transaction : en cas d'erreur, rien
   n'est appliqué ; corriger puis relancer le même script.
3. Lire les contrôles affichés en fin de script quand il y en a.
4. Déployer la nouvelle version de l'application, puis la démarrer.
5. Mettre à jour la colonne « Exécuté en PROD » ci-dessus (date).

## Règles pour un nouveau script

- Numéro suivant, nom explicite : `03_<sujet>.sql`.
- Tout dans `BEGIN; … COMMIT;`.
- Rejouable : `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE VIEW`…
- Le tester d'abord en PREPROD, puis l'ajouter au tableau.
- Ne plus modifier un script déjà exécuté en PROD : écrire un nouveau script.
