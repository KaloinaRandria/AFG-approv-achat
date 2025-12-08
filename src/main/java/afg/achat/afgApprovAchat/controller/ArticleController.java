package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.ArticleModificationDto;
import afg.achat.afgApprovAchat.model.CentreBudgetaire;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.util.UdmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/article")
public class ArticleController {
    @Autowired
    ArticleService articleService;
    @Autowired
    UdmService udmService;
    @Autowired
    FamilleService familleService;
    @Autowired
    private CentreBudgetaireService centreBudgetaireService;
    @Autowired
    IdGenerator idGenerator;


    @GetMapping("/list")
    public String listArticles(Model model) {
        Article[] articles = articleService.getAllArticles();
        Udm[] udms = udmService.getAllUdms();
        Famille[] familles = familleService.getAllFamilles();
        CentreBudgetaire[] centres = centreBudgetaireService.getAllCentreBudgetaires();

        model.addAttribute("articles", articles);
        model.addAttribute("udms", udms);
        model.addAttribute("familles", familles);
        model.addAttribute("centres", centres);

        // Ajouter un DTO vide pour le formulaire
        model.addAttribute("articleDto", new ArticleModificationDto());

        return "article/article-liste";
    }

    @GetMapping("/entree-saisie/{codeArticle}")
    public String entreeArticle(@PathVariable String codeArticle, Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle);
        model.addAttribute("article", article);
        return "stock/entree-saisie";
    }

    @GetMapping("/sortie-saisie/{codeArticle}")
    public String sortieArticle(@PathVariable String codeArticle, Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle);
        model.addAttribute("article", article);
        return "stock/sortie-saisie";
    }

    @GetMapping("/add")
    public String addArticlePage(Model model) {

        // Si l'article n'existe pas encore dans le modèle, en créer un nouveau
        if (!model.containsAttribute("article")) {
            model.addAttribute("article", new Article());
        }

        // Toujours ajouter les listes de données
        model.addAttribute("unites", udmService.getAllUdms());
        model.addAttribute("familles", familleService.getAllFamilles());
        model.addAttribute("centres", centreBudgetaireService.getAllCentreBudgetaires());

        return "article/article-saisie";
    }
    @PostMapping("/save")
    public String insertArticle(@ModelAttribute("article") Article article,
                                @RequestParam(name = "udm") String udmId,
                                @RequestParam(name = "famille") String familleId,
                                @RequestParam(name = "centreBudgetaire") String centreBudgetaireId,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {

        try {
            // Validation de base
            if (bindingResult.hasErrors()) {
                redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.article", bindingResult);
                redirectAttributes.addFlashAttribute("article", article);
                redirectAttributes.addFlashAttribute("ko", "Veuillez corriger les erreurs dans le formulaire");
                return "redirect:/admin/article/add";
            }

            if (article.getDesignation() == null || article.getDesignation().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "La désignation est obligatoire");
                redirectAttributes.addFlashAttribute("article", article);
                return "redirect:/admin/article/add";
            }

            if (article.getSeuilMin() <= 0) {
                redirectAttributes.addFlashAttribute("ko", "Le seuil minimum doit être positif");
                redirectAttributes.addFlashAttribute("article", article);
                return "redirect:/admin/article/add";
            }

            // Récupération des objets liés
            Udm udm = udmService.getUdmById(Integer.parseInt(udmId))
                    .orElseThrow(() -> new IllegalArgumentException("Unité de mesure invalide"));

            Famille famille = familleService.getFamilleById(Integer.parseInt(familleId))
                    .orElseThrow(() -> new IllegalArgumentException("Famille invalide"));

            CentreBudgetaire centreBudgetaire = centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(centreBudgetaireId))
                    .orElseThrow(() -> new IllegalArgumentException("Centre budgétaire invalide"));

            // Génération du code
            article.setCodeArticle(idGenerator);
            article.setUdm(udm);
            article.setFamille(famille);
            article.setCentreBudgetaire(centreBudgetaire);

            // Sauvegarde
            Article savedArticle = articleService.saveArticle(article);

            // Message de succès
            redirectAttributes.addFlashAttribute("ok",
                    "Article '" + savedArticle.getDesignation() +
                            "' ajouté avec succès (Code: " + savedArticle.getCodeArticle() + ")");

            return "redirect:/admin/article/list";

        } catch (IllegalArgumentException e) {
            // Pour les erreurs de validation, rediriger vers le formulaire
            redirectAttributes.addFlashAttribute("ko", "Erreur : " + e.getMessage());
            redirectAttributes.addFlashAttribute("article", article);
            return "redirect:/admin/article/add";

        } catch (Exception e) {
            // Pour les autres erreurs
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout de l'article: " + e.getMessage());
            redirectAttributes.addFlashAttribute("article", article);
            return "redirect:/admin/article/add";
        }
    }
    @PostMapping("/modifier")
    public String modifierArticle(@RequestParam(name = "codeArticle") String codeArticle,
                                  @RequestParam(name = "designation") String designation,
                                  @RequestParam(name = "idUdm") String idUdm,
                                  @RequestParam(name = "idFamille") String idFamille,
                                  @RequestParam(name = "idCentreBudgetaire") String idCentreBudgetaire,
                                  RedirectAttributes redirectAttributes){
        try {
            // Validation des données
            if (codeArticle == null || codeArticle.trim().isEmpty()) {
                throw new IllegalArgumentException("Le code article est obligatoire");
            }

            if (designation == null || designation.trim().isEmpty()) {
                throw new IllegalArgumentException("La désignation est obligatoire");
            }

            // Appel du service de modification
            Article articleModifie = articleService.modifierArticle(
                    codeArticle.trim(),
                    designation.trim(),
                    idUdm != null && !idUdm.isEmpty() ? idUdm : null,
                    idFamille != null && !idFamille.isEmpty() ? idFamille : null,
                    idCentreBudgetaire != null && !idCentreBudgetaire.isEmpty() ? idCentreBudgetaire : null
            );

            // Message de succès
            redirectAttributes.addFlashAttribute("ok",
                    "✅ Article <strong>" + articleModifie.getCodeArticle() + "</strong> modifié avec succès !");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur de validation : " + e.getMessage());
            redirectAttributes.addFlashAttribute("codeArticle", codeArticle);
            redirectAttributes.addFlashAttribute("designation", designation);
            redirectAttributes.addFlashAttribute("idUdm", idUdm);
            redirectAttributes.addFlashAttribute("idFamille", idFamille);
            redirectAttributes.addFlashAttribute("idCentreBudgetaire", idCentreBudgetaire);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de la modification : " + e.getMessage());
//            redirectAttributes.addFlashAttribute("codeArticle", codeArticle);
        }

        return "redirect:/admin/article/list";
    }
}
