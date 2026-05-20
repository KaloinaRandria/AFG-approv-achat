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


    @Transactional
    public void updatePrixUnitaire(String codeArticle, String prixUnitaireStr) {
        if (prixUnitaireStr == null || prixUnitaireStr.isBlank()) return;

        Article a = this.getArticleByCodeArticle(codeArticle)
                .orElseThrow(() -> new IllegalArgumentException("Article introuvable: " + codeArticle));

        a.setPrixUnitaire(prixUnitaireStr);
        articleRepo.save(a);
    }

    public Article getArticleByCodeProvisoire(String codeProvisoire) {
        return articleRepo.findArticlesByCodeProvisoire(codeProvisoire);
    }

}
