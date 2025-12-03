package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
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
        model.addAttribute("articles", articles);
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
        model.addAttribute("article", new Article());

        model.addAttribute("unites", udmService.getAllUdms());
        model.addAttribute("familles", familleService.getAllFamilles());
        model.addAttribute("centres",centreBudgetaireService.getAllCentreBudgetaires());

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
            if (bindingResult.hasErrors()) {
                model.addAttribute("unites", udmService.getAllUdms());
                model.addAttribute("familles", familleService.getAllFamilles());
                model.addAttribute("centres", centreBudgetaireService.getAllCentreBudgetaires());
                return "article/article-saisie";
            }
            if (article.getDesignation() == null || article.getDesignation().trim().isEmpty()) {
                bindingResult.rejectValue("designation", "notempty", "La désignation est obligatoire");
            }

            if (article.getSeuilMin() <= 0) {
                bindingResult.rejectValue("seuilMin", "min.value", "Le seuil minimum doit être positif");
            }

            if (bindingResult.hasErrors()) {
                model.addAttribute("unites", udmService.getAllUdms());
                model.addAttribute("familles", familleService.getAllFamilles());
                model.addAttribute("centres", centreBudgetaireService.getAllCentreBudgetaires());
                return "admin/article/article-saisie";
            }

            Udm udm = udmService.getUdmById(Integer.parseInt(udmId))
                    .orElseThrow(() -> new IllegalArgumentException("Unité de mesure invalide"));

            Famille famille = familleService.getFamilleById(Integer.parseInt(familleId))
                    .orElseThrow(() -> new IllegalArgumentException("Famille invalide"));

            CentreBudgetaire centreBudgetaire = centreBudgetaireService.getCentreBudgetaireById(Integer.parseInt(centreBudgetaireId))
                    .orElseThrow(() -> new IllegalArgumentException("Centre budgétaire invalide"));

            article.setCodeArticle(idGenerator);
            article.setUdm(udm);
            article.setFamille(famille);
            article.setCentreBudgetaire(centreBudgetaire);

            Article savedArticle = articleService.saveArticle(article);

            // Message de succès
            redirectAttributes.addFlashAttribute("successMessage",
                    "Article '" + savedArticle.getDesignation() + "' ajouté avec succès (Code: " + savedArticle.getCodeArticle() + ")");

            return "redirect:/admin/article/list";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("unites", udmService.getAllUdms());
            model.addAttribute("familles", familleService.getAllFamilles());
            model.addAttribute("centres",centreBudgetaireService.getAllCentreBudgetaires());

            return "article/article-saisie";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Erreur lors de l'ajout de l'article: " + e.getMessage());
            model.addAttribute("unites", udmService.getAllUdms());
            model.addAttribute("familles", familleService.getAllFamilles());
            model.addAttribute("centres", centreBudgetaireService.getAllCentreBudgetaires());

            return "admin/article/article-saisie";
        }

    }
}
