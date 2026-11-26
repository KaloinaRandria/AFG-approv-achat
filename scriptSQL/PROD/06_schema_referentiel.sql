-- ═════════════════════════════════════════════════════════════════════════════
-- 06 — Schéma "referentiel" : utilisateurs, postes, directions communs aux applis
--
-- Phase 1 de docs/plan-migration-referentiel.md. À exécuter EN TANT QUE postgres,
-- connecté à la base de l'appli achat (ACHAT_APPROV_MIGR pour la répétition, puis
-- ACHAT_APPROV_PREPROD, puis ACHAT_APPROV_PROD), APPLICATION ARRÊTÉE.
-- Prérequis : 05_login_applicatif.sql exécuté sur la même base.
--
-- Déplace 6 tables de public vers referentiel. ALTER TABLE … SET SCHEMA conserve les
-- données, les index, les contraintes, les séquences des colonnes identity et les droits
-- déjà donnés ; les 32 clés étrangères qui pointent vers ces tables et la vue
-- v_historique_mouvement_stock restent valides. Restent dans public : role et
-- utilisateur_role (habilitations propres à l'appli achat, décision D2).
--
-- La version de l'application qui va avec (entités en schema = "referentiel") doit être
-- déployée juste après : l'ancienne version ne trouverait plus les tables.
--
-- Rejouable : une table déjà déplacée est ignorée.
-- ═════════════════════════════════════════════════════════════════════════════

BEGIN;

CREATE SCHEMA IF NOT EXISTS referentiel AUTHORIZATION postgres;
COMMENT ON SCHEMA referentiel IS
    'Utilisateurs, postes, directions et valideurs communs aux applis achat et process. '
    'Seule l''appli achat y écrit (décision D1).';

-- ── Déplacement des tables ──────────────────────────────────────────────────
DO $$
DECLARE
    v_table text;
BEGIN
    FOREACH v_table IN ARRAY ARRAY['service', 'poste', 'pdp', 'utilisateur',
                                   'utilisateur_validateur', 'demande_compte']
    LOOP
        IF to_regclass('referentiel.' || v_table) IS NOT NULL THEN
            RAISE NOTICE 'referentiel.% existe déjà : ignorée', v_table;
        ELSIF to_regclass('public.' || v_table) IS NULL THEN
            RAISE EXCEPTION 'Table public.% introuvable : base inattendue, rien n''est appliqué', v_table;
        ELSE
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA referentiel', v_table);
            RAISE NOTICE 'public.% → referentiel.%', v_table, v_table;
        END IF;
    END LOOP;
END $$;

-- Statut des directions (utilisé par l'appli process, décision D3)
ALTER TABLE referentiel.service ADD COLUMN IF NOT EXISTS actif boolean NOT NULL DEFAULT true;

-- ── Droits du login de l'appli achat (propriétaire fonctionnel du référentiel) ─
DO $$
DECLARE
    v_login text := lower(current_database()) || '_app';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_login) THEN
        RAISE EXCEPTION 'Login % absent : exécuter d''abord 05_login_applicatif.sql', v_login;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA referentiel TO %I', v_login);
    -- Les droits donnés par le script 05 ont suivi les tables ; on les redonne pour
    -- couvrir une base où 05 aurait été exécuté après le déplacement.
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES '
                || 'ON ALL TABLES IN SCHEMA referentiel TO %I', v_login);
    EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA referentiel TO %I', v_login);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA referentiel '
                || 'GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES ON TABLES TO %I', v_login);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA referentiel '
                || 'GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I', v_login);
END $$;

-- ── Noms de tables sans préfixe ─────────────────────────────────────────────
-- Pour toutes les connexions à cette base (appli, pgAdmin, scripts) : "utilisateur"
-- désigne referentiel.utilisateur. Filet de sécurité pour le SQL écrit à la main ;
-- les entités Java, elles, indiquent le schéma explicitement.
-- Prend effet aux nouvelles connexions.
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET search_path = public, referentiel', current_database());
END $$;

COMMIT;

-- ── Contrôles ───────────────────────────────────────────────────────────────
-- 1. Les 6 tables sont dans referentiel, avec leurs lignes
SELECT 'referentiel.' || relname AS table_, n_live_tup AS lignes
FROM pg_stat_user_tables WHERE schemaname = 'referentiel' ORDER BY relname;

-- 2. Aucune table utilisateur restée dans public (doit être vide)
SELECT tablename AS reste_dans_public FROM pg_tables
WHERE schemaname = 'public'
  AND tablename IN ('service', 'poste', 'pdp', 'utilisateur', 'utilisateur_validateur', 'demande_compte');

-- 3. Clés étrangères vers le référentiel (32 attendues)
SELECT count(*) AS fk_vers_referentiel FROM pg_constraint c
JOIN pg_class t ON t.oid = c.confrelid JOIN pg_namespace n ON n.oid = t.relnamespace
WHERE c.contype = 'f' AND n.nspname = 'referentiel';

-- 4. La vue d'historique répond toujours
SELECT count(*) AS lignes_historique FROM v_historique_mouvement_stock;

-- ═════════════════════════════════════════════════════════════════════════════
-- RETOUR ARRIÈRE (seulement si la nouvelle version de l'appli doit être retirée) :
-- arrêter l'appli, exécuter le bloc ci-dessous, redéployer la version précédente.
--
-- BEGIN;
-- ALTER TABLE referentiel.demande_compte          SET SCHEMA public;
-- ALTER TABLE referentiel.utilisateur_validateur  SET SCHEMA public;
-- ALTER TABLE referentiel.utilisateur             SET SCHEMA public;
-- ALTER TABLE referentiel.pdp                     SET SCHEMA public;
-- ALTER TABLE referentiel.poste                   SET SCHEMA public;
-- ALTER TABLE referentiel.service                 SET SCHEMA public;
-- DO $$ BEGIN EXECUTE format('ALTER DATABASE %I RESET search_path', current_database()); END $$;
-- COMMIT;
-- (la colonne service.actif peut rester : l'ancienne version l'ignore)
-- ═════════════════════════════════════════════════════════════════════════════
