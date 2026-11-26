-- ═════════════════════════════════════════════════════════════════════════════
-- 05 — Login PostgreSQL dédié à l'application (à la place du superutilisateur postgres)
--
-- À exécuter EN TANT QUE postgres, CONNECTÉ À LA BASE CIBLE, une fois par base :
--   ACHAT_APPROV_PREPROD, ACHAT_APPROV_PROD, PROCESS_PREPROD, PROCESS_<PROD>…
-- (le même script se trouve dans AFG-process/scriptSQL/).
--
-- Le nom du login est déduit de la base : <nom de la base en minuscules>_app
--   ACHAT_APPROV_PREPROD → achat_approv_preprod_app
--   ACHAT_APPROV_PROD    → achat_approv_prod_app
-- PREPROD et PROD étant sur le même serveur, un login par environnement garantit qu'une
-- appli de préprod mal configurée ne peut pas écrire dans la base de prod.
--
-- Droits donnés : lecture / écriture des données du schéma public (tables, vues, séquences),
-- y compris les tables créées plus tard par postgres. AUCUN droit de modifier le schéma
-- (CREATE / ALTER / DROP) : les évolutions passent par les scripts de ce dossier.
--
-- Le mot de passe n'est PAS dans ce script (il serait dans Git). Après exécution :
--   ALTER ROLE achat_approv_preprod_app PASSWORD 'Test@1234';
-- ALTER ROLE achat_approv_migr_app PASSWORD 'Test@1234';
-- puis le reporter dans application-secret.properties (non suivi par Git) avec
--   spring.datasource.username=achat_approv_preprod_app
--
-- Rejouable : peut être relancé sans effet de bord.
-- ═════════════════════════════════════════════════════════════════════════════

BEGIN;

DO $$
DECLARE
    v_base  text := current_database();
    v_login text := lower(current_database()) || '_app';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_login) THEN
        EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION', v_login);
    END IF;

    -- Connexion : seulement à SA base. Par défaut PostgreSQL autorise tout le monde (PUBLIC)
    -- à se connecter à toutes les bases ; on retire ce droit sur cette base. Sans effet sur
    -- postgres (superutilisateur), seul compte utilisé aujourd'hui.
    EXECUTE format('REVOKE CONNECT, TEMPORARY ON DATABASE %I FROM PUBLIC', v_base);
    EXECUTE format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', v_base, v_login);

    -- Données existantes du schéma public
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', v_login);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES '
                || 'ON ALL TABLES IN SCHEMA public TO %I', v_login);          -- tables et vues
    EXECUTE format('GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO %I', v_login);
    EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO %I', v_login);

    -- Objets créés plus tard par postgres (scripts suivants)
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public '
                || 'GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES ON TABLES TO %I', v_login);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public '
                || 'GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I', v_login);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public '
                || 'GRANT EXECUTE ON FUNCTIONS TO %I', v_login);

    RAISE NOTICE 'Login % prêt pour la base %. Définir son mot de passe : ALTER ROLE % PASSWORD ''…'';',
        v_login, v_base, v_login;
END $$;

COMMIT;

-- Contrôle : droits du login sur les tables (doit lister toutes les tables, avec 6 droits chacune)
SELECT table_name, count(*) AS nb_droits
FROM information_schema.role_table_grants
WHERE grantee = lower(current_database()) || '_app' AND table_schema = 'public'
GROUP BY table_name
ORDER BY nb_droits, table_name;
