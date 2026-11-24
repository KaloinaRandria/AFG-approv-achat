-- ════════════════════════════════════════════════════════════════════════════
-- Optimisation des listes (état de stock, bons de commande, bons de livraison)
--
-- À exécuter une fois, dans une transaction, sur chaque base (PREPROD puis PROD),
-- APRÈS rattacher_bl_bon_commande.sql.
-- Le script est rejouable : chaque objet est créé « IF NOT EXISTS » ou remplacé,
-- et les totaux de stock_article sont recalculés depuis l'historique.
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- ── 1. Stock courant par article ────────────────────────────────────────────
-- Avant : v_etat_stock additionnait TOUS les mouvements (stock_fille) à chaque
-- affichage, avec deux jointures inutiles. Le coût grandissait avec l'historique.
-- Après : un total par article, tenu à jour par un trigger sur stock_fille ; la vue
-- ne lit plus qu'une ligne par article. Le trigger couvre tous les chemins du code
-- (BL, réception de BC, bons de sortie, saisies manuelles, mises à jour, suppressions).

CREATE TABLE IF NOT EXISTS stock_article (
    id_article   integer PRIMARY KEY REFERENCES article (id_article),
    total_entree double precision NOT NULL DEFAULT 0,
    total_sortie double precision NOT NULL DEFAULT 0
);

CREATE OR REPLACE FUNCTION maj_stock_article() RETURNS trigger AS $$
BEGIN
    -- Retire l'ancienne valeur (UPDATE, DELETE)
    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.id_article IS NOT NULL THEN
        UPDATE stock_article
           SET total_entree = total_entree - OLD.entree,
               total_sortie = total_sortie - OLD.sortie
         WHERE id_article = OLD.id_article;
    END IF;
    -- Ajoute la nouvelle valeur (INSERT, UPDATE)
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.id_article IS NOT NULL THEN
        INSERT INTO stock_article (id_article, total_entree, total_sortie)
        VALUES (NEW.id_article, NEW.entree, NEW.sortie)
        ON CONFLICT (id_article) DO UPDATE
           SET total_entree = stock_article.total_entree + EXCLUDED.total_entree,
               total_sortie = stock_article.total_sortie + EXCLUDED.total_sortie;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_fille_stock_article ON stock_fille;
CREATE TRIGGER trg_stock_fille_stock_article
    AFTER INSERT OR UPDATE OF entree, sortie, id_article OR DELETE ON stock_fille
    FOR EACH ROW EXECUTE FUNCTION maj_stock_article();

-- Reprise : totaux recalculés depuis l'historique (les mouvements en cours sont
-- bloqués le temps de la reprise pour ne rien compter deux fois)
LOCK TABLE stock_fille IN SHARE ROW EXCLUSIVE MODE;
TRUNCATE stock_article;
INSERT INTO stock_article (id_article, total_entree, total_sortie)
SELECT id_article, COALESCE(SUM(entree), 0), COALESCE(SUM(sortie), 0)
FROM stock_fille
WHERE id_article IS NOT NULL
GROUP BY id_article;

-- Même colonnes, mêmes types et même ordre qu'avant : le code Java ne change pas
CREATE OR REPLACE VIEW v_etat_stock AS
SELECT a.id_article,
       a.code_article,
       a.seuil_min,
       a.designation,
       COALESCE(s.total_entree, (0)::double precision)                  AS total_entree,
       COALESCE(s.total_sortie, (0)::double precision)                  AS total_sortie,
       COALESCE(s.total_entree - s.total_sortie, (0)::double precision) AS stock_disponible,
       u.acronyme    AS unite_de_mesure,
       u.description AS desc_udm
FROM article a
LEFT JOIN stock_article s ON s.id_article = a.id_article
LEFT JOIN udm u ON u.id_udm = a.id_udm
ORDER BY a.id_article;

-- ── 2. Index des listes ─────────────────────────────────────────────────────
-- Tri par date (pages de liste) et filtres utilisés par les recherches.
-- Les index du bon de livraison (date de réception, fournisseur, devise) existent déjà.

CREATE INDEX IF NOT EXISTS idx_bc_mere_date_creation ON bon_commande_mere (date_creation DESC, id_bc_mere DESC);
CREATE INDEX IF NOT EXISTS idx_bc_mere_statut_date   ON bon_commande_mere (statut, date_creation DESC);
CREATE INDEX IF NOT EXISTS idx_bc_mere_fournisseur   ON bon_commande_mere (id_fournisseur);
CREATE INDEX IF NOT EXISTS idx_bc_mere_demande       ON bon_commande_mere (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_bc_fille_bc_mere      ON bon_commande_fille (id_bc_mere);
CREATE INDEX IF NOT EXISTS idx_bc_fille_demande      ON bon_commande_fille (id_demande_fille);
CREATE INDEX IF NOT EXISTS idx_bc_fille_article      ON bon_commande_fille (id_article);
-- (idx_bl_mere_bc et idx_bl_fille_bc : créés par rattacher_bl_bon_commande.sql)
-- Lots encore disponibles d'un article (consommation FIFO des bons de sortie)
CREATE INDEX IF NOT EXISTS idx_lot_stock_dispo       ON lot_stock (id_article, date_entree) WHERE quantite_restante > 0;

COMMIT;

-- Statistiques à jour pour le planificateur
ANALYZE stock_article;
ANALYZE bon_commande_mere;
ANALYZE bon_commande_fille;
ANALYZE bon_livraison_mere;
ANALYZE bon_livraison_fille;
ANALYZE lot_stock;

-- ── Contrôle (à lancer après) : aucune ligne = totaux cohérents ─────────────
-- SELECT a.id_article, s.total_entree, x.e, s.total_sortie, x.s
-- FROM article a
-- LEFT JOIN stock_article s ON s.id_article = a.id_article
-- LEFT JOIN (SELECT id_article, SUM(entree) e, SUM(sortie) s FROM stock_fille GROUP BY id_article) x
--        ON x.id_article = a.id_article
-- WHERE ABS(COALESCE(s.total_entree, 0) - COALESCE(x.e, 0)) > 1e-6
--    OR ABS(COALESCE(s.total_sortie, 0) - COALESCE(x.s, 0)) > 1e-6;
