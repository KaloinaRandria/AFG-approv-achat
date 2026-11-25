-- ════════════════════════════════════════════════════════════════════════════
-- Migration PROD : alignement du schéma ACHAT_APPROV_PROD sur ACHAT_APPROV_PREPROD
--
-- Établi le 2026-11-25 en comparant les deux bases (lecture seule) : tables, colonnes,
-- contraintes, index, séquences, vues, fonctions, triggers et données de référence.
-- Il regroupe ce que les scripts suivants (et Hibernate, ddl-auto=update) ont fait en PREPROD :
--   creer_sequences_bon_commande.sql, rattacher_bl_bon_commande.sql, optimisation_listes.sql,
--   ajout_statut_utilisateur.sql, devise_documents.sql, date_mouvement_sortie.sql,
--   demandes_autres.sql, rattacher_postes_directions.sql, roles_maker_checker(_generiques).sql
--
-- Mode d'emploi :
--   1. Sauvegarder la base PROD (pg_dump) et arrêter l'application.
--   2. Exécuter ce script en entier (psql -f, ou pgAdmin en une seule exécution).
--      Tout est dans une seule transaction : en cas d'erreur, rien n'est appliqué.
--      Pour un essai à blanc, remplacer le COMMIT final par ROLLBACK.
--   3. Déployer la nouvelle version de l'application, puis la démarrer.
--
-- Rejouable : chaque objet n'est créé que s'il manque ; les vues et la fonction sont
-- remplacées à l'identique ; les totaux de stock_article sont recalculés.
-- Les noms des contraintes sont ceux de PREPROD (générés par Hibernate) : au démarrage,
-- ddl-auto=update les retrouve et n'en crée pas de doublons.
--
-- État de PROD constaté le 2026-11-25 : aucun bon de commande ni bon de sortie,
-- 129 mouvements de stock (stock_fille), 694 articles, 1 469 demandes, rôles 1 à 10.
-- ════════════════════════════════════════════════════════════════════════════

BEGIN;

-- Ajoute une contrainte si aucune contrainte de ce nom n'existe (outil de ce script, supprimé à la fin de la session)
CREATE OR REPLACE FUNCTION pg_temp.ajouter_contrainte(p_table text, p_nom text, p_def text) RETURNS void AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = p_nom AND conrelid = p_table::regclass) THEN
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s', p_table, p_nom, p_def);
    END IF;
END;
$$ LANGUAGE plpgsql;


-- ── 1. Utilisateurs, articles, postes ───────────────────────────────────────

-- Statut actif / inactif des comptes (un collaborateur parti est désactivé, jamais supprimé)
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS actif BOOLEAN NOT NULL DEFAULT TRUE;

-- Articles actifs : NULL = actif (articles antérieurs au champ)
ALTER TABLE article ADD COLUMN IF NOT EXISTS actif BOOLEAN;

-- Rattachement d'un poste à une direction
ALTER TABLE poste ADD COLUMN IF NOT EXISTS id_service INTEGER;
SELECT pg_temp.ajouter_contrainte('poste', 'fkfh7m5aeuh71p1g47b5sjdrvv4',
    'FOREIGN KEY (id_service) REFERENCES service (id_service)');


-- ── 2. Demandes : type, devise ──────────────────────────────────────────────

ALTER TABLE demande_mere ADD COLUMN IF NOT EXISTS type_demande VARCHAR(255);
ALTER TABLE demande_mere ADD COLUMN IF NOT EXISTS id_devise    INTEGER;
ALTER TABLE demande_mere ADD COLUMN IF NOT EXISTS taux_change  DOUBLE PRECISION;
ALTER TABLE demande_mere ADD COLUMN IF NOT EXISTS date_taux    TIMESTAMP;

SELECT pg_temp.ajouter_contrainte('demande_mere', 'fk2i7eil5d0rsahjsodyh3f5rrs',
    'FOREIGN KEY (id_devise) REFERENCES devise (id_devise)');
SELECT pg_temp.ajouter_contrainte('demande_mere', 'demande_mere_type_demande_check',
    $c$CHECK (type_demande IN ('FOURNITURE', 'AUTRE', 'STANDARD'))$c$);


-- ── 3. Bons de commande : circuit de validation, devise, réception ──────────

ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS etape_validation         VARCHAR(255);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_soumission          TIMESTAMP;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_soumis_par            INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_validation_mg       TIMESTAMP;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_valide_mg_par         INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_validation_sg       TIMESTAMP;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_valide_sg_par         INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_rejete_par            INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS motif_rejet              TEXT;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS modalite_paiement        VARCHAR(255);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS reference_approbation    VARCHAR(255);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_devise                INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS taux_change              DOUBLE PRECISION;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_taux                TIMESTAMP;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS statut_reception         VARCHAR(255);
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS motif_cloture_reception  TEXT;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS id_cloture_reception_par INTEGER;
ALTER TABLE bon_commande_mere ADD COLUMN IF NOT EXISTS date_cloture_reception   TIMESTAMP;

SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fkterp8l65cpw1s4oris2bbxvt8',
    'FOREIGN KEY (id_soumis_par) REFERENCES utilisateur (id_utilisateur)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fkbhiglj15r35pmghe237k3fctc',
    'FOREIGN KEY (id_valide_mg_par) REFERENCES utilisateur (id_utilisateur)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fks94yy5bx1owunk4ii7hu7b4br',
    'FOREIGN KEY (id_valide_sg_par) REFERENCES utilisateur (id_utilisateur)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fkog4um4u4v1dhos5ejukdjvs84',
    'FOREIGN KEY (id_rejete_par) REFERENCES utilisateur (id_utilisateur)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fk9a4321mjj6ho1w143ma4il5vt',
    'FOREIGN KEY (id_cloture_reception_par) REFERENCES utilisateur (id_utilisateur)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'fklgek1pm0mh7yyx995kt3nqcnb',
    'FOREIGN KEY (id_devise) REFERENCES devise (id_devise)');
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'bon_commande_mere_etape_validation_check',
    $c$CHECK (etape_validation IN ('MG', 'SG'))$c$);
SELECT pg_temp.ajouter_contrainte('bon_commande_mere', 'bon_commande_mere_statut_reception_check',
    $c$CHECK (statut_reception IN ('NON_RECU', 'PARTIELLEMENT_RECU', 'RECU', 'SOLDE'))$c$);

ALTER TABLE bon_commande_fille ADD COLUMN IF NOT EXISTS designation TEXT;
ALTER TABLE bon_commande_fille ADD COLUMN IF NOT EXISTS unite       VARCHAR(255);

-- Séquence des identifiants de bon de commande (BC00000001…) : aucun bon en PROD
CREATE SEQUENCE IF NOT EXISTS s_bc_mere START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
SELECT setval('s_bc_mere', x.max_id)
FROM (SELECT MAX(CAST(SUBSTRING(id_bc_mere FROM 3) AS BIGINT)) AS max_id
      FROM bon_commande_mere WHERE id_bc_mere ~ '^BC[0-9]+$') AS x
WHERE x.max_id IS NOT NULL;

-- Compteur annuel des numéros de bon de commande (JJMMAAAA-n)
CREATE TABLE IF NOT EXISTS compteur_bc (
    annee          INTEGER PRIMARY KEY,
    dernier_numero INTEGER NOT NULL
);


-- ── 4. Bons de livraison rattachés au bon de commande ──────────────────────

ALTER TABLE bon_livraison_mere  ADD COLUMN IF NOT EXISTS id_bc_mere  VARCHAR(255);
ALTER TABLE bon_livraison_fille ADD COLUMN IF NOT EXISTS id_bc_fille INTEGER;
SELECT pg_temp.ajouter_contrainte('bon_livraison_mere', 'fkahceje20aevrieyu2rxwdjc71',
    'FOREIGN KEY (id_bc_mere) REFERENCES bon_commande_mere (id_bc_mere)');
SELECT pg_temp.ajouter_contrainte('bon_livraison_fille', 'fksnf62voymik26axp1av1mvp9l',
    'FOREIGN KEY (id_bc_fille) REFERENCES bon_commande_fille (id_bc_fille)');


-- ── 5. Bons de sortie : annulation ──────────────────────────────────────────

ALTER TABLE bon_sortie_mere ADD COLUMN IF NOT EXISTS date_annulation  TIMESTAMP;
ALTER TABLE bon_sortie_mere ADD COLUMN IF NOT EXISTS id_annule_par    INTEGER;
ALTER TABLE bon_sortie_mere ADD COLUMN IF NOT EXISTS motif_annulation TEXT;
SELECT pg_temp.ajouter_contrainte('bon_sortie_mere', 'fkn889e5j8cqdoacfxhpvkqfsiy',
    'FOREIGN KEY (id_annule_par) REFERENCES utilisateur (id_utilisateur)');


-- ── 6. Demandes Autres : catégories de dépense et détail ───────────────────

CREATE TABLE IF NOT EXISTS categorie_depense (
    id_categorie_depense     INTEGER GENERATED BY DEFAULT AS IDENTITY,
    code                     VARCHAR(50)  NOT NULL,
    libelle                  VARCHAR(255) NOT NULL,
    beneficiaire_obligatoire BOOLEAN,
    actif                    BOOLEAN,
    ordre                    INTEGER,
    CONSTRAINT categorie_depense_pkey PRIMARY KEY (id_categorie_depense),
    CONSTRAINT ukoww9455v29qhex8yc2ku8bflc UNIQUE (code)
);

INSERT INTO categorie_depense (code, libelle, beneficiaire_obligatoire, actif, ordre) VALUES
    ('SPONSORING',        'Sponsoring',                   TRUE, TRUE, 10),
    ('REMBOURSEMENT',     'Remboursement',                TRUE, TRUE, 20),
    ('INDEMNITE_REPAS',   'Indemnité de repas',           TRUE, TRUE, 30),
    ('LOT_RECOMPENSE',    'Lot / Récompense',             TRUE, TRUE, 40),
    ('DROITS_HONORAIRES', 'Droits (divers / honoraires)', TRUE, TRUE, 50)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS demande_autre_detail (
    id_demande_autre_detail INTEGER GENERATED BY DEFAULT AS IDENTITY,
    id_demande_mere         VARCHAR(255) NOT NULL,
    id_categorie_depense    INTEGER      NOT NULL,
    beneficiaire            VARCHAR(255),
    quantite                DOUBLE PRECISION,
    montant_unitaire        DOUBLE PRECISION,
    montant                 DOUBLE PRECISION,
    CONSTRAINT demande_autre_detail_pkey PRIMARY KEY (id_demande_autre_detail),
    CONSTRAINT ukikw3r1p4cceb1huil1w24hnbq UNIQUE (id_demande_mere),
    CONSTRAINT fk9c3r8748t4xspud0atcjhek8n FOREIGN KEY (id_demande_mere) REFERENCES demande_mere (id_demande_mere),
    CONSTRAINT fki85iitdn1w4ne2m6tgfp09pio FOREIGN KEY (id_categorie_depense) REFERENCES categorie_depense (id_categorie_depense)
);

-- Modes de traitement imposés par le type de demande (déjà présents en PROD : contrôle seulement)
INSERT INTO mode_traitement (libelle, necessite_bon_commande)
SELECT 'Paiement direct', FALSE
WHERE NOT EXISTS (SELECT 1 FROM mode_traitement WHERE LOWER(libelle) = LOWER('Paiement direct'));
INSERT INTO mode_traitement (libelle, necessite_bon_commande)
SELECT 'En attente Moyens Généraux', TRUE
WHERE NOT EXISTS (SELECT 1 FROM mode_traitement WHERE LOWER(libelle) = LOWER('En attente Moyens Généraux'));


-- ── 7. Demandes de compte utilisateur (maker / checker) et rôles ───────────

CREATE TABLE IF NOT EXISTS demande_compte (
    id_demande_compte    INTEGER GENERATED BY DEFAULT AS IDENTITY,
    type_demande         VARCHAR(255) NOT NULL,
    statut               VARCHAR(255) NOT NULL,
    id_utilisateur_cible INTEGER,
    libelle_cible        VARCHAR(255),
    donnees              TEXT,
    id_demandeur         INTEGER      NOT NULL,
    date_demande         TIMESTAMP    NOT NULL,
    id_decideur          INTEGER,
    date_decision        TIMESTAMP,
    motif_rejet          TEXT,
    CONSTRAINT demande_compte_pkey PRIMARY KEY (id_demande_compte),
    CONSTRAINT demande_compte_statut_check
        CHECK (statut IN ('EN_ATTENTE', 'VALIDEE', 'REJETEE', 'ANNULEE')),
    CONSTRAINT demande_compte_type_demande_check
        CHECK (type_demande IN ('CREATION', 'MODIFICATION', 'ACTIVATION', 'DESACTIVATION')),
    CONSTRAINT fk39f14mn4llxu25mjsqeyegsul FOREIGN KEY (id_demandeur)         REFERENCES utilisateur (id_utilisateur),
    CONSTRAINT fk7nj9lfmnm6bxqu2k9wg58vwyo FOREIGN KEY (id_utilisateur_cible) REFERENCES utilisateur (id_utilisateur),
    CONSTRAINT fki69qrlvsj40k1tfq9smc3dbbp FOREIGN KEY (id_decideur)          REFERENCES utilisateur (id_utilisateur)
);
CREATE INDEX IF NOT EXISTS idx_demande_compte_statut ON demande_compte (statut);

-- Rôles génériques : le domaine vient du rôle métier (IT + MAKER, MOYENS_GENERAUX + CHECKER…).
-- Ils sont attribués ensuite depuis l'application (Gestion des utilisateurs).
INSERT INTO role (role) SELECT 'MAKER'   WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'MAKER');
INSERT INTO role (role) SELECT 'CHECKER' WHERE NOT EXISTS (SELECT 1 FROM role WHERE role = 'CHECKER');


-- ── 8. Stock courant par article (table tenue par trigger) ─────────────────

CREATE TABLE IF NOT EXISTS stock_article (
    id_article   INTEGER          NOT NULL,
    total_entree DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_sortie DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT stock_article_pkey PRIMARY KEY (id_article),
    CONSTRAINT stock_article_id_article_fkey FOREIGN KEY (id_article) REFERENCES article (id_article)
);

CREATE OR REPLACE FUNCTION maj_stock_article() RETURNS trigger
LANGUAGE plpgsql AS $function$
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
$function$;

DROP TRIGGER IF EXISTS trg_stock_fille_stock_article ON stock_fille;
CREATE TRIGGER trg_stock_fille_stock_article
    AFTER INSERT OR DELETE OR UPDATE OF entree, sortie, id_article ON stock_fille
    FOR EACH ROW EXECUTE FUNCTION maj_stock_article();

-- Reprise des totaux depuis l'historique des mouvements
LOCK TABLE stock_fille IN SHARE ROW EXCLUSIVE MODE;
TRUNCATE stock_article;
INSERT INTO stock_article (id_article, total_entree, total_sortie)
SELECT id_article, COALESCE(SUM(entree), 0), COALESCE(SUM(sortie), 0)
FROM stock_fille
WHERE id_article IS NOT NULL
GROUP BY id_article;


-- ── 9. Vues (définitions de PREPROD ; mêmes colonnes et types qu'en PROD) ──

-- État du stock : lit stock_article au lieu d'additionner tout l'historique
CREATE OR REPLACE VIEW v_etat_stock AS
 SELECT a.id_article,
    a.code_article,
    a.seuil_min,
    a.designation,
    COALESCE(s.total_entree, 0::double precision) AS total_entree,
    COALESCE(s.total_sortie, 0::double precision) AS total_sortie,
    COALESCE(s.total_entree - s.total_sortie, 0::double precision) AS stock_disponible,
    u.acronyme AS unite_de_mesure,
    u.description AS desc_udm
   FROM article a
     LEFT JOIN stock_article s ON s.id_article = a.id_article
     LEFT JOIN udm u ON u.id_udm = a.id_udm
  ORDER BY a.id_article;

-- Historique des mouvements : une ligne par bon de sortie confirmé, à sa date réelle
CREATE OR REPLACE VIEW v_historique_mouvement_stock AS
 WITH bs_confirme AS (
         SELECT bsf.id_bon_sortie_fille,
            bsf.id_article,
            bsf.quantite_sortie,
            bsm.id_demande_mere,
            bsm.numero_bs,
            COALESCE(bsm.date_confirmation, bsm.date_creation) AS date_sortie
           FROM bon_sortie_fille bsf
             JOIN bon_sortie_mere bsm ON bsm.id_bon_sortie_mere = bsf.id_bon_sortie_mere
          WHERE bsm.statut::text = 'CONFIRME'::text
        ), mouvements AS (
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
          WHERE sf.entree > 0::double precision OR sf.sortie > 0::double precision AND sm.id_demande_mere IS NULL
        UNION ALL
         SELECT - bs.id_bon_sortie_fille,
            bs.id_article,
            'SORTIE'::text AS text,
            bs.quantite_sortie,
            bs.date_sortie,
            NULL::character varying AS "varchar",
            dm.id_demande_mere,
            concat(u.prenom, ' ', u.nom)::character varying AS concat,
            bs.numero_bs::character varying AS numero_bs
           FROM bs_confirme bs
             JOIN demande_mere dm ON dm.id_demande_mere::text = bs.id_demande_mere::text
             LEFT JOIN utilisateur u ON u.id_utilisateur = dm.id_demandeur
        UNION ALL
         SELECT sf.id_stock_fille,
            sf.id_article,
            'SORTIE'::text AS text,
            sf.sortie - COALESCE(couvert.quantite, 0::double precision),
            COALESCE(dm.date_sortie, dm.date_demande) AS "coalesce",
            NULL::character varying AS "varchar",
            dm.id_demande_mere,
            concat(u.prenom, ' ', u.nom)::character varying AS concat,
            dm.id_demande_mere
           FROM stock_fille sf
             JOIN stock_mere sm ON sm.id_stock_mere = sf.id_stock_mere
             JOIN demande_mere dm ON dm.id_demande_mere::text = sm.id_demande_mere::text
             LEFT JOIN utilisateur u ON u.id_utilisateur = dm.id_demandeur
             LEFT JOIN LATERAL ( SELECT sum(bs.quantite_sortie) AS quantite
                   FROM bs_confirme bs
                  WHERE bs.id_demande_mere::text = sm.id_demande_mere::text AND bs.id_article = sf.id_article) couvert ON true
          WHERE COALESCE(sf.entree, 0::double precision) <= 0::double precision AND (sf.sortie - COALESCE(couvert.quantite, 0::double precision)) > 0.0001::double precision
        )
 SELECT m.id_stock_fille,
    m.id_article,
    a.code_article,
    a.designation,
    m.type_mouvement,
    m.quantite,
    m.date_mouvement,
    m.ref_bl_mere::character varying(255) AS ref_bl_mere,
    m.ref_demande_mere,
    m.auteur,
    m.reference,
    udm.acronyme AS udm,
    udm.description AS desc_udm
   FROM mouvements m
     JOIN article a ON a.id_article = m.id_article
     LEFT JOIN udm ON udm.id_udm = a.id_udm
  ORDER BY m.date_mouvement DESC;


-- ── 10. Index des listes ────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_bc_mere_date_creation ON bon_commande_mere (date_creation DESC, id_bc_mere DESC);
CREATE INDEX IF NOT EXISTS idx_bc_mere_statut_date   ON bon_commande_mere (statut, date_creation DESC);
CREATE INDEX IF NOT EXISTS idx_bc_mere_fournisseur   ON bon_commande_mere (id_fournisseur);
CREATE INDEX IF NOT EXISTS idx_bc_mere_demande       ON bon_commande_mere (id_demande_mere);
CREATE INDEX IF NOT EXISTS idx_bc_fille_bc_mere      ON bon_commande_fille (id_bc_mere);
CREATE INDEX IF NOT EXISTS idx_bc_fille_demande      ON bon_commande_fille (id_demande_fille);
CREATE INDEX IF NOT EXISTS idx_bc_fille_article      ON bon_commande_fille (id_article);
CREATE INDEX IF NOT EXISTS idx_lot_stock_dispo       ON lot_stock (id_article, date_entree) WHERE quantite_restante > 0;


-- ── 11. Contrôles (à lire avant le COMMIT) ──────────────────────────────────

-- Objets attendus : tous « OK »
SELECT nom, CASE WHEN existe THEN 'OK' ELSE 'MANQUANT' END AS etat
FROM (VALUES
    ('table categorie_depense',          to_regclass('categorie_depense')    IS NOT NULL),
    ('table compteur_bc',                to_regclass('compteur_bc')          IS NOT NULL),
    ('table demande_autre_detail',       to_regclass('demande_autre_detail') IS NOT NULL),
    ('table demande_compte',             to_regclass('demande_compte')       IS NOT NULL),
    ('table stock_article',              to_regclass('stock_article')        IS NOT NULL),
    ('séquence s_bc_mere',               to_regclass('s_bc_mere')            IS NOT NULL),
    ('trigger stock_fille → stock_article',
        EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_stock_fille_stock_article')),
    ('rôles MAKER et CHECKER',
        (SELECT COUNT(*) FROM role WHERE role IN ('MAKER', 'CHECKER')) = 2),
    ('5 catégories de dépense',
        (SELECT COUNT(*) FROM categorie_depense) >= 5)
) AS objets(nom, existe);

-- Stock : aucune ligne = totaux de stock_article identiques à l'historique
SELECT a.id_article, s.total_entree, x.e AS entree_historique, s.total_sortie, x.s AS sortie_historique
FROM article a
LEFT JOIN stock_article s ON s.id_article = a.id_article
LEFT JOIN (SELECT id_article, SUM(entree) e, SUM(sortie) s FROM stock_fille GROUP BY id_article) x
       ON x.id_article = a.id_article
WHERE ABS(COALESCE(s.total_entree, 0) - COALESCE(x.e, 0)) > 1e-6
   OR ABS(COALESCE(s.total_sortie, 0) - COALESCE(x.s, 0)) > 1e-6;

COMMIT;

-- Statistiques à jour pour le planificateur (hors transaction)
ANALYZE stock_article;
ANALYZE bon_commande_mere;
ANALYZE bon_commande_fille;
ANALYZE lot_stock;


-- ════════════════════════════════════════════════════════════════════════════
-- FACULTATIF — données propres à PREPROD, NON reprises ci-dessus (décision métier) :
--
--   * Articles actifs : en PREPROD, seuls 215 articles sont actifs
--     (activer_articles_mouvement_stock.sql). En PROD, après ce script, tous les articles
--     restent actifs (actif NULL = actif). Lancer ce script séparément si la liste vaut pour PROD.
--
--   * Rattachement des postes aux directions : fait à la main en PREPROD (6 postes), depuis
--     l'écran Paramétrage des opérations > Directions et postes ; à refaire en PROD.
--
--   * Attribution de MAKER / CHECKER aux utilisateurs : depuis Gestion des utilisateurs.
-- ════════════════════════════════════════════════════════════════════════════
