-- ═════════════════════════════════════════════════════════════════════════════
-- Reprise des données de PROCESS_PREPROD dans le schéma process — PREPROD UNIQUEMENT
--
-- Phase 2 de docs/plan-migration-referentiel.md. À exécuter EN TANT QUE postgres,
-- connecté à ACHAT_APPROV_MIGR (répétition) puis ACHAT_APPROV_PREPROD, APRÈS
-- scriptSQL/PROD/07_schema_process.sql. Ne concerne pas la PROD (process n'y a jamais
-- tourné, décision D7).
--
-- État de PROCESS_PREPROD relevé le 26/11/2026 (lecture seule) :
--   - 147 utilisateurs, TOUS présents dans referentiel.utilisateur (même adresse mail) ;
--     2 écarts, issus de tests dans process, ignorés car le référentiel fait foi (D1) :
--       abdoul.malik@afgbank.mg       prénom « Said Ibrahim test » dans process
--       aina.razafinimanana@afgbank.mg  désactivé dans process, actif dans le référentiel
--   - 20 directions, toutes présentes dans referentiel.service (code = acronyme) ;
--   - 4 rôles, 11 attributions de rôles, 2 demandes (validées) ;
--   - procédures, questions, choix, évaluations : AUCUNE ligne (rien à reprendre).
-- Si des données ont été saisies dans PROCESS_PREPROD depuis, refaire ce relevé.
--
-- Les utilisateurs sont retrouvés par leur adresse mail, les directions par leur code :
-- les identifiants de PROCESS_PREPROD ne sont pas réutilisés (sauf ceux des demandes).
-- Rejouable : ce qui existe déjà est ignoré.
-- ═════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ── Rôles de l'appli process (l'appli les crée aussi au démarrage s'ils manquent) ──
INSERT INTO process.role (role)
VALUES ('ADMIN'), ('IT'), ('MAKER'), ('CHECKER')
ON CONFLICT (role) DO NOTHING;

-- ── Attributions de rôles (11) ──────────────────────────────────────────────
CREATE TEMP TABLE reprise_role (mail text, role text) ON COMMIT DROP;
INSERT INTO reprise_role VALUES
    ('kaloina.randriambololona@afgbank.mg', 'ADMIN'),
    ('mialy.rasamimanana@afgbank.mg',       'ADMIN'),
    ('yves.rakotondrazaka@afgbank.mg',      'ADMIN'),
    ('aime.randriatsiresy@afgbank.mg',      'CHECKER'),
    ('aime.randriatsiresy@afgbank.mg',      'IT'),
    ('haja.razafindralambo@afgbank.mg',     'IT'),
    ('judicael.pierre@afgbank.mg',          'IT'),
    ('mendrika.randriamalala@afgbank.mg',   'IT'),
    ('tsiory.ratsima@afgbank.mg',           'IT'),
    ('judicael.pierre@afgbank.mg',          'MAKER'),
    ('mendrika.randriamalala@afgbank.mg',   'MAKER');

DO $$
DECLARE
    v_absents text;
BEGIN
    SELECT string_agg(DISTINCT r.mail, ', ') INTO v_absents
    FROM reprise_role r
    WHERE NOT EXISTS (SELECT 1 FROM referentiel.utilisateur u
                      WHERE lower(trim(u.mail)) = lower(trim(r.mail)));
    IF v_absents IS NOT NULL THEN
        RAISE EXCEPTION 'Utilisateurs absents du référentiel : %. Rien n''est appliqué.', v_absents;
    END IF;
END $$;

INSERT INTO process.utilisateur_role (id_utilisateur, id_role)
SELECT u.id_utilisateur, ro.id_role
FROM reprise_role r
JOIN referentiel.utilisateur u ON lower(trim(u.mail)) = lower(trim(r.mail))
JOIN process.role ro ON ro.role = r.role
ON CONFLICT DO NOTHING;

-- ── Demandes (2, validées) : historique maker / checker ─────────────────────
INSERT INTO process.demande_utilisateur
    (id_demande, actif, commentaire, date_demande, date_traitement, mail, nom, prenom,
     statut, type, id_checker, id_direction, id_maker, id_utilisateur)
SELECT d.id_demande, d.actif, NULL, d.date_demande, d.date_traitement, d.mail, d.nom, d.prenom,
       d.statut, d.type,
       (SELECT id_utilisateur FROM referentiel.utilisateur WHERE lower(trim(mail)) = d.checker),
       (SELECT id_service     FROM referentiel.service     WHERE acronyme = d.direction),
       (SELECT id_utilisateur FROM referentiel.utilisateur WHERE lower(trim(mail)) = d.maker),
       (SELECT id_utilisateur FROM referentiel.utilisateur WHERE lower(trim(mail)) = d.mail)
FROM (VALUES
    (1, false, timestamp '2026-11-25 11:52:35.57388',  timestamp '2026-11-25 11:53:06.96154',
     'aina.razafinimanana@afgbank.mg', 'RAZAFINIMANANA', 'Aina Liantsoa',
     'VALIDEE', 'MODIFICATION', 'aime.randriatsiresy@afgbank.mg', 'DAMG', 'judicael.pierre@afgbank.mg'),
    (2, true,  timestamp '2026-11-26 14:19:59.411488', timestamp '2026-11-26 14:20:35.460159',
     'abdoul.malik@afgbank.mg', 'ABDOUL MALIK', 'Said Ibrahim test',
     'VALIDEE', 'MODIFICATION', 'aime.randriatsiresy@afgbank.mg', 'DRH', 'judicael.pierre@afgbank.mg')
) AS d (id_demande, actif, date_demande, date_traitement, mail, nom, prenom,
        statut, type, checker, direction, maker)
ON CONFLICT (id_demande) DO NOTHING;

-- Les prochaines demandes repartent après les identifiants repris
SELECT setval(pg_get_serial_sequence('process.demande_utilisateur', 'id_demande'),
              (SELECT coalesce(max(id_demande), 0) + 1 FROM process.demande_utilisateur), false);

COMMIT;

-- ── Contrôles ───────────────────────────────────────────────────────────────
-- Attendu : 4 rôles, 11 attributions, 2 demandes avec maker, checker, cible et direction
SELECT (SELECT count(*) FROM process.role)             AS roles,
       (SELECT count(*) FROM process.utilisateur_role) AS attributions,
       (SELECT count(*) FROM process.demande_utilisateur
         WHERE id_maker IS NOT NULL AND id_checker IS NOT NULL
           AND id_utilisateur IS NOT NULL AND id_direction IS NOT NULL) AS demandes_completes;

SELECT ro.role, u.mail
FROM process.utilisateur_role ur
JOIN process.role ro ON ro.id_role = ur.id_role
JOIN referentiel.utilisateur u ON u.id_utilisateur = ur.id_utilisateur
ORDER BY 1, 2;
