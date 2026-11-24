-- Statut actif / inactif des utilisateurs.
-- Un collaborateur parti est désactivé (jamais supprimé) : il ne peut plus se
-- connecter ni recevoir de tâches ou de mails, et son historique reste intact.
-- Idempotent : peut être relancé sans risque.

ALTER TABLE utilisateur
    ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;

-- Contrôle : répartition des comptes
SELECT actif, COUNT(*) FROM utilisateur GROUP BY actif;
