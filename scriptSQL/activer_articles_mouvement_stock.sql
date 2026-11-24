-- Articles actifs : seuls les 215 articles du fichier « FICHIERS MOUVEMENT STOCK.xlsx » restent actifs.
-- Tous les autres articles passent inactifs : ils restent rattachés aux anciennes demandes
-- mais ne sont plus proposés dans les saisies ni suivis en stock.
--
-- Codes relevés dans la colonne « code article » des feuilles FRN BUREAU, MAT INFO, CONSO,
-- ENCRE, DIVERS, MONETIQUE ET GAB et FRN BANQUE (doublons retirés).
--
-- Prérequis : la colonne article.actif existe (créée au démarrage de l'application).
-- Le script est rejouable. Lisez le résultat des contrôles avant de valider : remplacez
-- COMMIT par ROLLBACK en fin de script pour faire un essai sans rien modifier.

BEGIN;

CREATE TEMP TABLE articles_actifs (code_article VARCHAR(50) PRIMARY KEY) ON COMMIT DROP;

INSERT INTO articles_actifs (code_article) VALUES
    ('ART00002820'), ('ART00002821'), ('ART00002822'), ('ART00002824'), ('ART00002825'), ('ART00002826'),
    ('ART00002835'), ('ART00002836'), ('ART00002837'), ('ART00002839'), ('ART00002840'), ('ART00002869'),
    ('ART00002870'), ('ART00002885'), ('ART00002886'), ('ART00002887'), ('ART00002888'), ('ART00002905'),
    ('ART00002906'), ('ART00002909'), ('ART00002912'), ('ART00002919'), ('ART00002920'), ('ART00002921'),
    ('ART00002922'), ('ART00002929'), ('ART00002931'), ('ART00002941'), ('ART00002960'), ('ART00002966'),
    ('ART00002985'), ('ART00002997'), ('ART00002998'), ('ART00003004'), ('ART00003005'), ('ART00003007'),
    ('ART00003012'), ('ART00003013'), ('ART00003014'), ('ART00003015'), ('ART00003016'), ('ART00003017'),
    ('ART00003018'), ('ART002348'), ('ART002349'), ('ART002350'), ('ART002351'), ('ART002352'),
    ('ART002353'), ('ART002354'), ('ART002355'), ('ART002356'), ('ART002357'), ('ART002358'),
    ('ART002359'), ('ART002360'), ('ART002361'), ('ART002362'), ('ART002363'), ('ART002364'),
    ('ART002365'), ('ART002366'), ('ART002367'), ('ART002368'), ('ART002369'), ('ART002370'),
    ('ART002371'), ('ART002374'), ('ART002375'), ('ART002376'), ('ART002377'), ('ART002378'),
    ('ART002379'), ('ART002382'), ('ART002385'), ('ART002386'), ('ART002387'), ('ART002389'),
    ('ART002391'), ('ART002393'), ('ART002394'), ('ART002395'), ('ART002398'), ('ART002403'),
    ('ART002404'), ('ART002412'), ('ART002419'), ('ART002420'), ('ART002437'), ('ART002438'),
    ('ART002439'), ('ART002440'), ('ART002441'), ('ART002442'), ('ART002443'), ('ART002444'),
    ('ART002445'), ('ART002446'), ('ART002447'), ('ART002448'), ('ART002449'), ('ART002450'),
    ('ART002451'), ('ART002452'), ('ART002453'), ('ART002454'), ('ART002455'), ('ART002456'),
    ('ART002457'), ('ART002458'), ('ART002459'), ('ART002460'), ('ART002461'), ('ART002462'),
    ('ART002463'), ('ART002464'), ('ART002465'), ('ART002466'), ('ART002467'), ('ART002468'),
    ('ART002469'), ('ART002470'), ('ART002471'), ('ART002472'), ('ART002473'), ('ART002474'),
    ('ART002475'), ('ART002476'), ('ART002477'), ('ART002478'), ('ART002479'), ('ART002480'),
    ('ART002481'), ('ART002482'), ('ART002483'), ('ART002484'), ('ART002485'), ('ART002486'),
    ('ART002487'), ('ART002488'), ('ART002489'), ('ART002490'), ('ART002491'), ('ART002492'),
    ('ART002493'), ('ART002494'), ('ART002495'), ('ART002496'), ('ART002497'), ('ART002498'),
    ('ART002499'), ('ART002500'), ('ART002501'), ('ART002502'), ('ART002503'), ('ART002504'),
    ('ART002505'), ('ART002506'), ('ART002507'), ('ART002508'), ('ART002509'), ('ART002510'),
    ('ART002511'), ('ART002512'), ('ART002513'), ('ART002515'), ('ART002516'), ('ART002517'),
    ('ART002518'), ('ART002519'), ('ART002520'), ('ART002521'), ('ART002522'), ('ART002523'),
    ('ART002524'), ('ART002525'), ('ART002526'), ('ART002528'), ('ART002529'), ('ART002530'),
    ('ART002531'), ('ART002532'), ('ART002534'), ('ART002535'), ('ART002563'), ('ART002565'),
    ('ART002566'), ('ART002571'), ('ART002572'), ('ART002573'), ('ART002577'), ('ART002579'),
    ('ART002591'), ('ART002603'), ('ART002604'), ('ART002605'), ('ART002606'), ('ART002607'),
    ('ART002609'), ('ART002610'), ('ART002612'), ('ART002613'), ('ART002614'), ('ART002615'),
    ('ART002616'), ('ART002617'), ('ART002618'), ('ART002619'), ('ART002620'), ('ART002621'),
    ('ART002622'), ('ART002633'), ('ART002634'), ('ART002638'), ('ART002641');

-- Contrôle 1 : codes du fichier introuvables dans la table article (doit être vide)
SELECT f.code_article AS code_du_fichier_introuvable
FROM articles_actifs f
WHERE NOT EXISTS (
    SELECT 1 FROM article a WHERE UPPER(TRIM(a.code_article)) = f.code_article
)
ORDER BY f.code_article;

-- Mise à jour : actif pour les articles du fichier, inactif pour tous les autres
UPDATE article a
SET actif = EXISTS (
    SELECT 1 FROM articles_actifs f WHERE f.code_article = UPPER(TRIM(a.code_article))
);

-- Contrôle 2 : répartition après mise à jour (actif = true attendu : 215)
SELECT actif, COUNT(*) AS nombre_articles
FROM article
GROUP BY actif
ORDER BY actif DESC;

-- Contrôle 3 : articles devenus inactifs qui ont encore du stock (à examiner)
SELECT v.code_article, v.designation, v.stock_disponible
FROM v_etat_stock v
JOIN article a ON a.id_article = v.id_article
WHERE a.actif = false
  AND CAST(v.stock_disponible AS numeric) > 0
ORDER BY v.code_article;

COMMIT;