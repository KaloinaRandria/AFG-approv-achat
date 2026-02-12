create sequence s_code_article
    start with 1
    increment by 1
    no minvalue
    no maxvalue
    cache 1;

create sequence demande_mere_id_demande_mere_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue
    cache 1;

create sequence bon_livraison_mere_id_bl_mere_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue
    cache 1;

create sequence bon_sortie_mere_id_bon_sortie_mere_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue
    cache 1;


-- View Pour afficher l'état du stock
CREATE OR REPLACE VIEW v_etat_stock AS
SELECT
    a.id_article,
    a.code_article,
    a.seuil_min,
    a.designation,
    COALESCE(SUM(sf.entree), 0) AS total_entree,
    COALESCE(SUM(sf.sortie), 0) AS total_sortie,
    COALESCE(SUM(sf.entree - sf.sortie), 0) AS stock_disponible,
    u.acronyme AS unite_de_mesure,
    u.description AS desc_udm
FROM article a
         LEFT JOIN stock_fille sf ON sf.id_article = a.id_article
         LEFT JOIN stock_mere sm ON sm.id_stock_mere = sf.id_stock_mere
         LEFT JOIN bon_livraison_mere bl ON bl.id_bl_mere = sm.id_bl_mere
         LEFT JOIN demande_mere dm ON dm.id_demande_mere = sm.id_demande_mere
         LEFT JOIN udm u ON u.id_udm = a.id_udm
GROUP BY a.id_article, u.id_udm
ORDER BY a.id_article;

CREATE OR REPLACE VIEW v_historique_mouvement_stock AS
SELECT
    sf.id_stock_fille,
    sf.id_article,
    a.code_article,
    a.designation,

    -- Type
    CASE
        WHEN COALESCE(sf.entree, 0) > 0 THEN 'ENTREE'
        WHEN COALESCE(sf.sortie, 0) > 0 THEN 'SORTIE'
        ELSE 'INCONNU'
        END AS type_mouvement,

    -- Quantité
    CASE
        WHEN COALESCE(sf.entree, 0) > 0 THEN sf.entree
        ELSE sf.sortie
        END AS quantite,

    -- Date mouvement (entrée = date réception BL, sortie = date sortie demande)
    CASE
        WHEN COALESCE(sf.entree, 0) > 0 THEN bl.date_reception
        WHEN COALESCE(sf.sortie, 0) > 0 THEN COALESCE(dm.date_sortie, dm.date_demande)
        ELSE NULL
        END AS date_mouvement,

    -- Références
    bl.id_bl_mere       AS ref_bl_mere,
    dm.id_demande_mere  AS ref_demande_mere,

    -- Qui a fait (entrée = fournisseur, sortie = demandeur)
    CASE
        WHEN COALESCE(sf.entree, 0) > 0 THEN f.nom  -- adapte le champ exact (ex: f.nom / f.raison_sociale)
        WHEN COALESCE(sf.sortie, 0) > 0 THEN CONCAT(u.prenom, ' ', u.nom)
        ELSE '-'
        END AS auteur,

    -- extra utile
    CASE
        WHEN COALESCE(sf.entree, 0) > 0 THEN bl.id_bl_mere
        WHEN COALESCE(sf.sortie, 0) > 0 THEN dm.id_demande_mere
        ELSE NULL
        END AS reference,

    udm.acronyme AS udm,
    udm.description AS desc_udm

FROM stock_fille sf
         JOIN stock_mere sm            ON sm.id_stock_mere = sf.id_stock_mere
         JOIN article a                ON a.id_article = sf.id_article
         LEFT JOIN bon_livraison_mere bl ON bl.id_bl_mere = sm.id_bl_mere
         LEFT JOIN fournisseur f       ON f.id_fournisseur = bl.id_fournisseur
         LEFT JOIN demande_mere dm     ON dm.id_demande_mere = sm.id_demande_mere
         LEFT JOIN utilisateur u       ON u.id_utilisateur = dm.id_demandeur
         LEFT JOIN udm                 ON udm.id_udm = a.id_udm

WHERE sf.entree > 0 OR sf.sortie > 0

ORDER BY date_mouvement DESC;

SELECT conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
         JOIN pg_class t ON t.oid = c.conrelid
WHERE t.relname = 'demande_mere'
  AND c.conname = 'demande_mere_priorite_check';

ALTER TABLE demande_mere DROP CONSTRAINT demande_mere_priorite_check;

ALTER TABLE demande_mere
    ADD CONSTRAINT demande_mere_priorite_check
        CHECK (priorite IN ('P1','P2','P3'));


-- Listes des INDEX :
-- demandes
CREATE INDEX IF NOT EXISTS idx_demande_mere_demandeur   ON demande_mere (id_demandeur);
CREATE INDEX IF NOT EXISTS idx_demande_mere_centre      ON demande_mere (id_centre_budgetaire);

CREATE INDEX IF NOT EXISTS idx_demande_fille_mere       ON demande_fille (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_demande_fille_article    ON demande_fille (id_article);

CREATE INDEX IF NOT EXISTS idx_validation_demande_mere  ON validation_demande (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_validation_demande_valid ON validation_demande (id_validateur);

CREATE INDEX IF NOT EXISTS idx_piece_jointe_mere        ON piece_jointe (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_demande_pj_mere          ON demande_piece_jointe (id_demande_mere);

-- Bons , Stock
CREATE INDEX IF NOT EXISTS idx_stock_fille_stock_mere   ON stock_fille (id_stock_mere);
CREATE INDEX IF NOT EXISTS idx_stock_fille_article      ON stock_fille (id_article);

CREATE INDEX IF NOT EXISTS idx_stock_mere_bl_mere       ON stock_mere (id_bl_mere);
CREATE INDEX IF NOT EXISTS idx_stock_mere_demande_mere  ON stock_mere (id_demande_mere);

CREATE INDEX IF NOT EXISTS idx_bl_fille_bl_mere         ON bon_livraison_fille (id_bl_mere);
CREATE INDEX IF NOT EXISTS idx_bl_fille_article         ON bon_livraison_fille (id_article);

CREATE INDEX IF NOT EXISTS idx_bon_sortie_mere_dm        ON bon_sortie_mere (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_bon_sortie_fille_mere     ON bon_sortie_fille (id_bon_sortie_mere);
CREATE INDEX IF NOT EXISTS idx_bon_sortie_fille_article  ON bon_sortie_fille (id_article);

-- historiques
CREATE INDEX IF NOT EXISTS idx_article_famille          ON article (id_famille);
CREATE INDEX IF NOT EXISTS idx_article_udm              ON article (id_udm);

CREATE INDEX IF NOT EXISTS idx_article_hist_article     ON article_historique (id_article);
CREATE INDEX IF NOT EXISTS idx_fourn_hist_fournisseur   ON fournisseur_historique (id_fournisseur);
CREATE INDEX IF NOT EXISTS idx_devise_hist_devise       ON devise_historique (id_devise);

CREATE INDEX IF NOT EXISTS idx_utilisateur_pdp          ON utilisateur (id_pdp);
CREATE INDEX IF NOT EXISTS idx_utilisateur_superieur    ON utilisateur (id_superieur);
CREATE INDEX IF NOT EXISTS idx_utilisateur_poste        ON utilisateur (id_poste);

-- perf tri
CREATE INDEX IF NOT EXISTS idx_demande_mere_date         ON demande_mere (date_demande DESC);
CREATE INDEX IF NOT EXISTS idx_demande_mere_statut       ON demande_mere (statut);
CREATE INDEX IF NOT EXISTS idx_demande_mere_date_statut  ON demande_mere (statut, date_demande DESC);

-- stock fille
CREATE INDEX IF NOT EXISTS idx_stock_fille_article_stockmere ON stock_fille (id_article, id_stock_mere);

CREATE INDEX IF NOT EXISTS idx_stock_fille_entree_pos
    ON stock_fille (id_stock_mere, id_article)
    WHERE entree > 0;

CREATE INDEX IF NOT EXISTS idx_stock_fille_sortie_pos
    ON stock_fille (id_stock_mere, id_article)
    WHERE sortie > 0;




CREATE INDEX IF NOT EXISTS idx_bl_mere_date_reception
    ON bon_livraison_mere (date_reception DESC);

CREATE INDEX IF NOT EXISTS idx_dm_date_sortie
    ON demande_mere (date_sortie DESC);

CREATE INDEX IF NOT EXISTS idx_dm_date_demande
    ON demande_mere (date_demande DESC);


CREATE INDEX IF NOT EXISTS idx_bl_mere_fournisseur
    ON bon_livraison_mere (id_fournisseur);
CREATE INDEX IF NOT EXISTS idx_bl_mere_devise
    ON bon_livraison_mere (id_devise);

CREATE INDEX IF NOT EXISTS idx_validation_validateur_date
    ON validation_demande (id_validateur, date_action DESC);
