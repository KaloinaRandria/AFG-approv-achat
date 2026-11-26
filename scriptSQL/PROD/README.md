# Scripts SQL à exécuter en PROD

Base cible : **ACHAT_APPROV_PROD**. Ce dossier ne contient que les scripts qui restent à
exécuter en production, dans l'ordre de leur numéro.

## Ordre d'exécution

| N° | Script | Contenu | Exécuté en PREPROD | Exécuté en PROD |
|----|--------|---------|--------------------|-----------------|
| 01 | `01_alignement_schema_preprod.sql` | Alignement du schéma PROD sur la PREPROD au 25/11/2026 (regroupe les anciens scripts de `scriptSQL/`) | oui | **non** |
| 02 | `02_inventaire_stock.sql` | Inventaires du stock : tables, lien `stock_mere` → `inventaire`, vue de l'historique des mouvements | oui (25/11/2026) | **non** |
| 03 | `03_contre_valeurs_ariary.sql` | Contre-valeurs en ariary enregistrées sur les demandes, bons de commande et bons de livraison, avec reprise de l'existant | non | **non** |
| 04 | `04_devise_xof.sql` | Ajout de la devise XOF (franc CFA BCEAO), cours déduit du cours EUR (parité fixe 655,957) | oui (26/11/2026) | **non** |
| 05 | `05_login_applicatif.sql` | Login PostgreSQL dédié à l'appli (`achat_approv_prod_app`), sans droit de modifier le schéma ; mot de passe à définir à part, puis à reporter dans `application-secret.properties` | **non** | **non** |

> Depuis le 26/11/2026, l'application démarre en `ddl-auto=validate` : elle ne crée plus ni
> table ni colonne. Si un script n'a pas été exécuté, elle refuse de démarrer avec un message
> `Schema-validation: missing table [...]` ou `missing column [...]` : exécuter le script manquant.

## Mode d'emploi

1. Sauvegarder la base PROD (`pg_dump`) et arrêter l'application.
2. Exécuter les scripts **dans l'ordre des numéros**, chacun en entier (psql -f, ou pgAdmin
   en une seule exécution). Chaque script est dans une transaction : en cas d'erreur, rien
   n'est appliqué ; corriger puis relancer le même script.
3. Lire les contrôles affichés en fin de script quand il y en a.
4. Déployer la nouvelle version de l'application, puis la démarrer.
5. Mettre à jour la colonne « Exécuté en PROD » ci-dessus (date).

## Règles pour un nouveau script

- Numéro suivant, nom explicite : `06_<sujet>.sql`.
- Tout dans `BEGIN; … COMMIT;`.
- Rejouable : `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE VIEW`…
- Le tester d'abord en PREPROD, puis l'ajouter au tableau.
- Toute nouvelle entité ou colonne Java a son script ici : l'appli ne crée plus rien elle-même.
- Ne plus modifier un script déjà exécuté en PROD : écrire un nouveau script.
