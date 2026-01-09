package afg.achat.afgApprovAchat.service;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.util.ArticleHistorique;
import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.ArticleRepo;
import afg.achat.afgApprovAchat.repository.CentreBudgetaireRepo;
import afg.achat.afgApprovAchat.repository.FamilleRepo;
import afg.achat.afgApprovAchat.repository.util.ArticleHistoriqueRepo;
import afg.achat.afgApprovAchat.repository.util.UdmRepo;
import afg.achat.afgApprovAchat.service.util.ArticleHistoriqueService;
import afg.achat.afgApprovAchat.service.util.UdmService;
import jakarta.transaction.Transactional;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
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
    CentreBudgetaireService centreBudgetaireService;
    @Autowired
    ArticleHistoriqueService articleHistoriqueService;

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
                                   String idUdm, String idFamille, String idCentreBudgetaire) {
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

        // 4. Centre budgétaire
        if (idCentreBudgetaire != null && !idCentreBudgetaire.isEmpty() && !"null".equals(idCentreBudgetaire)) {
            try {
                CentreBudgetaire nouveauCentre = centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(idCentreBudgetaire))
                        .orElseThrow(() -> new IllegalArgumentException("Centre budgétaire invalide"));
                CentreBudgetaire ancienCentre = article.getCentreBudgetaire();

                if (nouveauCentre != null && !nouveauCentre.equals(ancienCentre)) {
                    historiques.add(new ArticleHistorique(
                            article,
                            "Centre budgétaire",
                            ancienCentre != null ? ancienCentre.getCodeCentre() : "null",
                            nouveauCentre.getCodeCentre(),
                            utilisateur,
                            article.getCodeArticle()
                    ));
                    article.setCentreBudgetaire(nouveauCentre);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("ID Centre budgétaire invalide: " + idCentreBudgetaire);
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
}
