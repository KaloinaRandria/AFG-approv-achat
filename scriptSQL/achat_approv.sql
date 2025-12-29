ALTER TABLE utilisateur
    ADD CONSTRAINT chk_superieur CHECK (id_superieur IS NULL OR id_superieur <> id_utilisateur);

select
    u.id_utilisateur as id_utilisateur,
    u.nom as nom_utilisateur,
    u.prenom as prenom_utilisateur,
    u.mail as mail_utilisateur,
    r.role as role_utilisateur
from utilisateur_role
join public.role r on r.id_role = utilisateur_role.id_role
join public.utilisateur u on u.id_utilisateur = utilisateur_role.id_utilisateur;

select * from article_id_article_seq;


select gsf.id_gisement_stock_fille,
       gsf.date_mouvement,
       gsf.quantite_in,
       gsf.quantite_out,
       a.designation
from gisement_stock_fille as gsf
join public.article a on a.id_article = gsf.id_article;

create sequence s_code_article
    start with 1
    increment by 1
    no minvalue
    no maxvalue
    cache 1;



-- SIMULATION STOCK
INSERT INTO utilisateur(nom, prenom, mail)
VALUES ('Randria','Kaloina','kaloina.randriambololona@afgbank.mg');

INSERT INTO role(role)
VALUES ('ADMIN');

INSERT INTO utilisateur_role(id_utilisateur, id_role)
VALUES (1, 1);

INSERT INTO fournisseur (nom, mail, contact)
VALUES ('Fourniline SARL', 'contact@fourniline.mg', '0321234567');

INSERT INTO udm (acronyme, description)
VALUES
    ('PCS', 'Pièce'),
    ('KG',  'Kilogramme'),
    ('L',   'Litre'),
    ('M',   'Mètre'),
    ('PCK', 'Pack / Ensemble'),
    ('CTN', 'Carton'),
    ('RLL', 'Rouleau'),
    ('PR',  'Paire'),
    ('SET', 'Ensemble / Kit'),
    ('BX',  'Boîte');

INSERT INTO centre_budgetaire (code_centre)
VALUES
    ('DG'),        -- Direction Générale
    ('FIN'),       -- Département Finance
    ('ACH'),       -- Achats / Approvisionnement
    ('LOG'),       -- Logistique / Stock
    ('IT'),        -- Informatique
    ('RH'),        -- Ressources Humaines
    ('MTN'),       -- Maintenance
    ('PRD'),       -- Production
    ('QHSE'),      -- Qualité, Hygiène, Sécurité, Environnement
    ('VNT');       -- Ventes / Commercial

INSERT INTO famille (description)
VALUES
    ('Fournitures de bureau'),
    ('Informatique et Électronique'),
    ('Mobilier'),
    ('Hygiène et Entretien'),
    ('Équipements de Protection (EPI)'),
    ('Matériaux de Construction'),
    ('Pièces détachées'),
    ('Consommables divers'),
    ('Produits alimentaires'),
    ('Services et Prestations');


INSERT INTO article (code_article, designation, seuil_min, id_famille, id_udm, id_centre_budgetaire)
VALUES
    ('ART-001', 'Imprimante HP', 5, 1, 1, 1),
    ('ART-002', 'Clavier Logitech', 10, 1, 1, 1),
    ('ART-003', 'Souris Sans Fil', 10, 1, 1, 1),
    ('ART-004', 'Unité Centrale Dell', 3, 1, 1, 1),
    ('ART-005', 'Ecran Samsung 24"', 5, 1, 1, 1),
    ('ART-006', 'Routeur Mikrotik', 2, 1, 1, 1),
    ('ART-007', 'Cable RJ45 2m', 20, 1, 1, 1),
    ('ART-008', 'Disque SSD 512Go', 5, 1, 1, 1),
    ('ART-009', 'RAM DDR4 8Go', 10, 1, 1, 1),
    ('ART-010', 'Stabilisateur APC', 3, 1, 1, 1);

INSERT INTO devise (acronyme, designation)
VALUES
    ('MGA', 'Ariary Malagasy'),
    ('EUR', 'Euro'),
    ('USD', 'Dollar Américain'),
    ('GBP', 'Livre Sterling'),
    ('ZAR', 'Rand Sud-Africain'),
    ('CHF', 'Franc Suisse'),
    ('CNY', 'Yuan Chinois'),
    ('JPY', 'Yen Japonais'),
    ('KES', 'Shilling Kenyan'),
    ('INR', 'Roupie Indienne');


INSERT INTO bon_livraison_mere (date_reception, description, id_fournisseur, id_devise)
VALUES (NOW(), 'Réception matériel informatique', 1, 1);



INSERT INTO stock_mere (id_bl_mere)
VALUES (1);


INSERT INTO stock_fille (entree, sortie, id_article, id_stock_mere)
VALUES
    (10, 0, 1, 1),  -- Imprimantes
    (25, 0, 2, 1),  -- Claviers
    (30, 0, 3, 1),  -- Souris
    (8,  0, 4, 1),  -- UC Dell
    (15, 0, 5, 1),  -- Ecrans
    (5,  0, 6, 1),  -- Routeurs
    (50, 0, 7, 1);  -- Câbles RJ45


INSERT INTO adresse (adresse)
VALUES
    ('Rez-de-chaussée - Bloc A'),
    ('1er étage - Bloc A'),
    ('2e étage - Bloc A'),
    ('3e étage - Bloc A'),
    ('Rez-de-chaussée - Bloc B'),
    ('1er étage - Bloc B'),
    ('2e étage - Bloc B'),
    ('Salle 101 - Bloc Administratif'),
    ('Salle 203 - Bloc Technique'),
    ('Magasin Central - RDC');


INSERT INTO demande_mere (date_demande, est_valider, nature_demande, id_demandeur, id_adresse)
VALUES (NOW(), TRUE, 'OPEX', 1, 1);


INSERT INTO stock_mere (id_demande_mere)
VALUES (1);



INSERT INTO stock_fille (entree, sortie, id_article, id_stock_mere)
VALUES (0, 2, 1, 2), -- 2 imprimantes sorties
       (0, 5, 2, 2), -- 5 claviers
       (0, 3, 3, 2);   -- 3 souris

-- Test nanao sortie anah stock
INSERT INTO stock_fille (entree, sortie, id_article, id_stock_mere)
VALUES (0,7,5,2);

INSERT INTO stock_fille (entree, sortie, id_article, id_stock_mere)
VALUES (0,25,7,2);



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
    u.acronyme AS unite_de_mesure
FROM article a
         LEFT JOIN stock_fille sf ON sf.id_article = a.id_article
         LEFT JOIN stock_mere sm ON sm.id_stock_mere = sf.id_stock_mere
         LEFT JOIN bon_livraison_mere bl ON bl.id_bl_mere = sm.id_bl_mere
         LEFT JOIN demande_mere dm ON dm.id_demande_mere = sm.id_demande_mere
         LEFT JOIN udm u ON u.id_udm = a.id_udm
GROUP BY a.id_article, u.id_udm
ORDER BY a.id_article;

select * from stock_fille;
select * from v_etat_stock;

INSERT INTO departement (nom, acronyme) VALUES
                                            ('Direction Générale', 'DG'),
                                            ('Finance et Comptabilité', 'FIN'),
                                            ('Ressources Humaines', 'RH'),
                                            ('Achats et Approvisionnement', 'AA'),
                                            ('Informatique', 'IT'),
                                            ('Systèmes d’Information', 'SI'),
                                            ('Audit Interne', 'AUDIT'),
                                            ('Contrôle de Gestion', 'CG'),
                                            ('Marketing', 'MKT'),
                                            ('Commercial', 'COM'),
                                            ('Logistique', 'LOG'),
                                            ('Juridique', 'JUR'),
                                            ('Communication', 'COMMU'),
                                            ('Maintenance', 'MAIN'),
                                            ('Production', 'PROD');

