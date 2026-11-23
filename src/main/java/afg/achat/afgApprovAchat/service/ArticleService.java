package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import afg.achat.afgApprovAchat.model.util.MontantCalculator;
import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.service.util.ArticleHistoriqueService;
import afg.achat.afgApprovAchat.service.util.UdmService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ArticleService {
    @Autowired
    ArticleRepo articleRepo;
    @Autowired
    UdmService udmService;
    @Autowired
    FamilleService familleService;
    @Autowired
    ArticleHistoriqueService articleHistoriqueService;

    @PersistenceContext
    private EntityManager entityManager;

    public Article[] getAllArticles() {
        return articleRepo.findAll().toArray(new Article[0]);
    }

    public Optional<Article> getArticleByCodeArticle(String codeArticle) {
        return articleRepo.findArticleByCodeArticle(codeArticle);
    }

    public Article saveArticle(Article article) {
        articleRepo.save(article);
        return article;
    }

    public Optional<Article> getArticleByDesignation(String designation) {
        return articleRepo.findArticleByDesignation(designation);
    }

    @Transactional
    public Article modifierArticle(String codeArticle, String designation, String seuilMin,
                                   String idUdm, String idFamille) {
        // Récupérer l'article existant
        Article article = this.getArticleByCodeArticle(codeArticle)
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

        if (article == null) {
            throw new RuntimeException("Article non trouvé avec le code: " + codeArticle);
        }

        // Récupérer l'utilisateur courant
        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String utilisateur = user != null ? user.getNom() + " " + user.getPrenom() : "Admin";

        List<ArticleHistorique> historiques = new ArrayList<>();

        // Vérifier et mettre à jour chaque champ avec historique
        // 1. Désignation
        if (designation != null && !designation.equals(article.getDesignation())) {
            historiques.add(new ArticleHistorique(
                    article,
                    "Désignation",
                    article.getDesignation(),
                    designation,
                    utilisateur,
                    article.getCodeArticle()
            ));
            article.setDesignation(designation);
        }
        // 2. Seuil Minimum
        if (seuilMin != null && Integer.parseInt(seuilMin) != article.getSeuilMin()) {
            historiques.add(new ArticleHistorique(
                    article,
                    "Seuil Minimum",
                    String.valueOf(article.getSeuilMin()),
                    seuilMin,
                    utilisateur,
                    article.getCodeArticle()
            ));
            article.setSeuilMin(Integer.parseInt(seuilMin));
        }

        // 2. Unité de mesure
        if (idUdm != "null" && !idUdm.isEmpty() && !"null".equals(idUdm)) {
            try {
                Udm nouvelleUdm = udmService.getUdmById(Integer.parseInt(idUdm))
                        .orElseThrow(() -> new IllegalArgumentException("Unité de mesure invalide"));
                Udm ancienneUdm = article.getUdm();

                if (nouvelleUdm != null && !nouvelleUdm.equals(ancienneUdm)) {
                    historiques.add(new ArticleHistorique(
                            article,
                            "Unité de mesure",
                            ancienneUdm != null ? ancienneUdm.getDescription() : "null",
                            nouvelleUdm.getDescription(),
                            utilisateur,
                            article.getCodeArticle()
                    ));
                    article.setUdm(nouvelleUdm);
                }
            } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID UDM invalide: " + idUdm);
            }
        }

        // 3. Famille
        if (idFamille != null && !idFamille.isEmpty() && !"null".equals(idFamille)) {
            try {
                Famille nouvelleFamille = familleService.getFamilleById(Integer.parseInt(idFamille))
                        .orElseThrow(() -> new IllegalArgumentException("Famille invalide"));
                Famille ancienneFamille = article.getFamille();

                if (nouvelleFamille != null && !nouvelleFamille.equals(ancienneFamille)) {
                    historiques.add(new ArticleHistorique(
                            article,
                            "Famille",
                            ancienneFamille != null ? ancienneFamille.getDescription() : "null",
                            nouvelleFamille.getDescription(),
                            utilisateur,
                            article.getCodeArticle()
                    ));
                    article.setFamille(nouvelleFamille);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("ID Famille invalide: " + idFamille);
            }
        }


        Article articleModifie = article;
        if (!historiques.isEmpty()) {
            articleModifie = articleRepo.save(article);

            // Sauvegarder l'historique - CORRECTION ICI
            for (ArticleHistorique historique : historiques) {
                articleHistoriqueService.saveArticleHistorique(historique);
            }
        }

        return articleModifie;
    }

    public List<Article> search(String q) {
        return articleRepo.findByCodeArticleContainingIgnoreCaseOrDesignationContainingIgnoreCase(q, q);
    }

    /**
     * Recherche d'articles actifs pour les saisies (demande, bon de livraison, bon de commande).
     *
     * <p>Chaque mot saisi doit se trouver dans le code ou dans la désignation, dans n'importe
     * quel ordre, sans tenir compte de la casse ni des accents : « encre cyan » trouve
     * « ART002363 - Encre HP415A Cyan », « electrique » trouve « Électrique ».</p>
     */
    public List<Article> rechercherPourSaisie(String saisie, int limite) {
        List<String> mots = saisie == null ? List.of()
                : java.util.Arrays.stream(sansAccents(saisie.trim().toLowerCase()).split("\\s+"))
                        .filter(m -> !m.isBlank())
                        .toList();
        if (mots.isEmpty()) return List.of();

        org.springframework.data.jpa.domain.Specification<Article> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> conditions = new java.util.ArrayList<>();
            // Articles actifs (sans indicateur : actifs)
            conditions.add(cb.or(cb.isNull(root.get("actif")), cb.isTrue(root.get("actif"))));
            var code = sansAccentsSql(cb, cb.lower(cb.coalesce(root.<String>get("codeArticle"), "")));
            var designation = sansAccentsSql(cb, cb.lower(cb.coalesce(root.<String>get("designation"), "")));
            for (String mot : mots) {
                String motif = "%" + echapperLike(mot) + "%";
                conditions.add(cb.or(cb.like(code, motif, '\\'), cb.like(designation, motif, '\\')));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return articleRepo.findAll(spec,
                org.springframework.data.domain.PageRequest.of(0, limite,
                        org.springframework.data.domain.Sort.by("designation", "codeArticle")))
                .getContent();
    }

    // Lettres accentuées (minuscules, après LOWER) et leur équivalent sans accent, caractère pour caractère
    private static final String ACCENTUES     = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿœæ";
    private static final String NON_ACCENTUES = "aaaaaaceeeeiiiinooooouuuuyyoa";

    /** Retire les accents en base avec translate() (l'extension unaccent n'est pas installée). */
    private static jakarta.persistence.criteria.Expression<String> sansAccentsSql(
            jakarta.persistence.criteria.CriteriaBuilder cb, jakarta.persistence.criteria.Expression<String> texte) {
        return cb.function("translate", String.class, texte, cb.literal(ACCENTUES), cb.literal(NON_ACCENTUES));
    }

    /** Même correspondance que {@link #sansAccentsSql} pour les mots saisis. */
    private static String sansAccents(String texte) {
        StringBuilder sb = new StringBuilder(texte.length());
        for (char c : texte.toCharArray()) {
            int i = ACCENTUES.indexOf(c);
            sb.append(i >= 0 ? NON_ACCENTUES.charAt(i) : c);
        }
        return sb.toString();
    }

    /** Les caractères joker du LIKE saisis par l'utilisateur sont cherchés tels quels. */
    private static String echapperLike(String mot) {
        return mot.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Page<Article> searchArticlesMulti(
            String code,
            String designation,
            String famille,
            String udm,
            Pageable pageable
    ) {
        String c = (code == null) ? "" : code.trim();
        String d = (designation == null) ? "" : designation.trim();
        String f = (famille == null) ? "" : famille.trim();
        String u = (udm == null) ? "" : udm.trim();

        SearchSession session = Search.session((Session) entityManager);

        // Calcul offset/limit depuis le Pageable Spring
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        SearchResult<Article> result = session
                .search(Article.class)
                .where(factory -> factory.bool(b -> {

                    // Si aucun critère → retourne tout
                    boolean aucunCritere = c.isEmpty() && d.isEmpty() && f.isEmpty() && u.isEmpty();
                    if (aucunCritere) {
                        b.must(factory.matchAll());
                        return;
                    }

                    // Code article
                    if (!c.isEmpty()) {
                        b.must(factory.wildcard()
                                .field("codeArticle_kw")
                                .matching("*" + c.toLowerCase() + "*"));
                    }

                    // Désignation
                    if (!d.isEmpty()) {
                        b.must(factory.match()
                                .field("designation")
                                .matching(d)
                                .fuzzy(1));  // tolère 1 faute de frappe
                    }

                    // Famille
                    if (!f.isEmpty()) {
                        b.must(factory.match()
                                .field("famille.description")
                                .matching(f)
                                .fuzzy(1));
                    }

                    // UDM — cherche dans description ET acronyme
                    if (!u.isEmpty()) {
                        b.must(factory.bool(inner -> inner
                                .should(factory.match()
                                        .field("udm.description")
                                        .matching(u)
                                        .fuzzy(1))
                                .should(factory.wildcard()
                                        .field("udm.acronyme")
                                        .matching("*" + u.toLowerCase() + "*"))
                        ));
                    }
                }))
                .sort(f2 -> f2.score())   // tri par pertinence
                .fetch(offset, limit);

        // Reconstruire un Page<Article> Spring compatible
        long total = result.total().hitCount();
        return new org.springframework.data.domain.PageImpl<>(
                result.hits(),
                pageable,
                total
        );
    }


    public Article getArticleByCodeProvisoire(String codeProvisoire) {
        return articleRepo.findArticlesByCodeProvisoire(codeProvisoire);
    }

}
