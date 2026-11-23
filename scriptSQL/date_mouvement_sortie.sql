-- Historique des mouvements de stock : une ligne par sortie, à sa date réelle.
--
-- Avant : les sorties d'une demande venaient de stock_fille, qui cumule les sorties d'un
-- article par demande. Deux bons de sortie de 5 apparaissaient comme une seule sortie de 10,
-- datée de la date de sortie souhaitée de la demande (et non du moment de la sortie).
--
-- Maintenant :
--   - chaque ligne de bon de sortie CONFIRMÉ est un mouvement de sortie, daté de la
--     confirmation du bon, avec le numéro du bon en référence (les bons annulés, remis en
--     stock, n'apparaissent pas) ;
--   - les entrées et les mouvements hors demande restent lus dans stock_fille, inchangés ;
--   - une sortie de demande antérieure aux bons de sortie (quantité de stock_fille non
--     couverte par des bons confirmés) reste affichée pour ce reliquat, à l'ancienne date.
--
-- Identifiant : id_stock_fille pour les lignes de stock_fille, opposé de
-- id_bon_sortie_fille (négatif) pour les sorties : unique, et du même type qu'avant.
-- Mêmes colonnes, même ordre : remplacement direct de la vue.
-- Idempotent : peut être relancé sans risque.

CREATE OR REPLACE VIEW v_historique_mouvement_stock AS
WITH bs_confirme AS (
    -- Lignes des bons de sortie confirmés
    SELECT bsf.id_bon_sortie_fille,
           bsf.id_article,
           bsf.quantite_sortie,
           bsm.id_demande_mere,
           bsm.numero_bs,
           COALESCE(bsm.date_confirmation, bsm.date_creation) AS date_sortie
    FROM bon_sortie_fille bsf
         JOIN bon_sortie_mere bsm ON bsm.id_bon_sortie_mere = bsf.id_bon_sortie_mere
    WHERE bsm.statut::text = 'CONFIRME'
),
mouvements AS (
    -- 1. Lignes de stock_fille : entrées, et sorties hors demande (comme avant)
    SELECT sf.id_stock_fille,
           sf.id_article,
           CASE
               WHEN COALESCE(sf.entree, 0::double precision) > 0::double precision THEN 'ENTREE'::text
               WHEN COALESCE(sf.sortie, 0::double precision) > 0::double precision THEN 'SORTIE'::text
               ELSE 'INCONNU'::text
           END AS type_mouvement,
           CASE
               WHEN COALESCE(sf.entree, 0::double precision) > 0::double precision THEN sf.entree
               ELSE sf.sortie
           END AS quantite,
           CASE
               WHEN COALESCE(sf.entree, 0::double precision) > 0::double precision THEN bl.date_reception
               WHEN COALESCE(sf.sortie, 0::double precision) > 0::double precision THEN COALESCE(dm.date_sortie, dm.date_demande)
               ELSE NULL::timestamp without time zone
           END AS date_mouvement,
           bl.id_bl_mere AS ref_bl_mere,
           dm.id_demande_mere AS ref_demande_mere,
           CASE
               WHEN COALESCE(sf.entree, 0::double precision) > 0::double precision THEN f.nom
               WHEN COALESCE(sf.sortie, 0::double precision) > 0::double precision THEN concat(u.prenom, ' ', u.nom)::character varying
               ELSE '-'::character varying
           END AS auteur,
           CASE
               WHEN COALESCE(sf.entree, 0::double precision) > 0::double precision THEN bl.id_bl_mere
               WHEN COALESCE(sf.sortie, 0::double precision) > 0::double precision THEN dm.id_demande_mere
               ELSE NULL::character varying
           END AS reference
    FROM stock_fille sf
         JOIN stock_mere sm ON sm.id_stock_mere = sf.id_stock_mere
         LEFT JOIN bon_livraison_mere bl ON bl.id_bl_mere::text = sm.id_bl_mere::text
         LEFT JOIN fournisseur f ON f.id_fournisseur = bl.id_fournisseur
         LEFT JOIN demande_mere dm ON dm.id_demande_mere::text = sm.id_demande_mere::text
         LEFT JOIN utilisateur u ON u.id_utilisateur = dm.id_demandeur
    WHERE sf.entree > 0::double precision
       OR (sf.sortie > 0::double precision AND sm.id_demande_mere IS NULL)

    UNION ALL

    -- 2. Sorties de demande : une ligne par ligne de bon de sortie confirmé
    SELECT -bs.id_bon_sortie_fille,
           bs.id_article,
           'SORTIE'::text,
           bs.quantite_sortie,
           bs.date_sortie,
           NULL::character varying,
           dm.id_demande_mere,
           concat(u.prenom, ' ', u.nom)::character varying,
           bs.numero_bs::character varying
    FROM bs_confirme bs
         JOIN demande_mere dm ON dm.id_demande_mere::text = bs.id_demande_mere::text
         LEFT JOIN utilisateur u ON u.id_utilisateur = dm.id_demandeur

    UNION ALL

    -- 3. Sorties de demande antérieures aux bons de sortie : reliquat non couvert, ancienne date
    SELECT sf.id_stock_fille,
           sf.id_article,
           'SORTIE'::text,
           sf.sortie - COALESCE(couvert.quantite, 0::double precision),
           COALESCE(dm.date_sortie, dm.date_demande),
           NULL::character varying,
           dm.id_demande_mere,
           concat(u.prenom, ' ', u.nom)::character varying,
           dm.id_demande_mere
    FROM stock_fille sf
         JOIN stock_mere sm ON sm.id_stock_mere = sf.id_stock_mere
         JOIN demande_mere dm ON dm.id_demande_mere::text = sm.id_demande_mere::text
         LEFT JOIN utilisateur u ON u.id_utilisateur = dm.id_demandeur
         LEFT JOIN LATERAL (
             SELECT SUM(bs.quantite_sortie) AS quantite
             FROM bs_confirme bs
             WHERE bs.id_demande_mere::text = sm.id_demande_mere::text
               AND bs.id_article = sf.id_article
         ) couvert ON true
    WHERE COALESCE(sf.entree, 0::double precision) <= 0::double precision
      AND sf.sortie - COALESCE(couvert.quantite, 0::double precision) > 0.0001::double precision
)
-- Types identiques à ceux de la vue existante (CREATE OR REPLACE refuse tout changement de type,
-- y compris la longueur d'un varchar que l'UNION fait perdre)
SELECT m.id_stock_fille::integer AS id_stock_fille,
       m.id_article::integer AS id_article,
       a.code_article,
       a.designation,
       m.type_mouvement::text AS type_mouvement,
       m.quantite::double precision AS quantite,
       m.date_mouvement::timestamp without time zone AS date_mouvement,
       m.ref_bl_mere::character varying(255) AS ref_bl_mere,
       m.ref_demande_mere::character varying(255) AS ref_demande_mere,
       m.auteur::character varying AS auteur,
       m.reference::character varying AS reference,
       udm.acronyme AS udm,
       udm.description AS desc_udm
FROM mouvements m
     JOIN article a ON a.id_article = m.id_article
     LEFT JOIN udm ON udm.id_udm = a.id_udm
ORDER BY m.date_mouvement DESC;
