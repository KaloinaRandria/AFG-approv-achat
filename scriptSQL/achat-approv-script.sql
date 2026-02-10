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

WHERE COALESCE(sf.entree, 0) > 0 OR COALESCE(sf.sortie, 0) > 0

ORDER BY date_mouvement DESC;

