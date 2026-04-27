package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.DTO.ArticleModificationDTO;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.model.util.Udm;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.util.UdmService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    ArticleService articleService;
    @Autowired
    UdmService udmService;
    @Autowired
    FamilleService familleService;
    @Autowired
    IdGenerator idGenerator;


    @GetMapping("/list")
    public String listArticles(
            Model model,
            HttpServletRequest request,

            @RequestParam(required = false) String code,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String famille,
            @RequestParam(required = false) String udm,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "codeArticle") String sort,
            @RequestParam(defaultValue = "asc") String dir
    ) {
        // ✅ sécuriser le sort (important)
        if (!Set.of("codeArticle", "designation", "seuilMin").contains(sort)) {
            sort = "codeArticle";
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;


        Pageable pageable = PageRequest.of(page, size);

        Page<Article> articlesPage = articleService.searchArticlesMulti(code, designation, famille, udm, pageable);

        model.addAttribute("articlesPage", articlesPage);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);

        //garder valeurs dans inputs
        model.addAttribute("code", code == null ? "" : code);
        model.addAttribute("designation", designation == null ? "" : designation);
        model.addAttribute("famille", famille == null ? "" : famille);
        model.addAttribute("udm", udm == null ? "" : udm);

        model.addAttribute("udms", udmService.getAllUdms());
        model.addAttribute("familles", familleService.getAllFamilles());
        model.addAttribute("articleDto", new ArticleModificationDTO());

        return "article/article-liste";
    }

    @GetMapping("/entree-saisie/{codeArticle}")
    public String entreeArticle(@PathVariable String codeArticle, Model model, HttpServletRequest request) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        model.addAttribute("article", article);
        return "stock/entree-saisie";
    }

    @GetMapping("/sortie-saisie/{codeArticle}")
    public String sortieArticle(@PathVariable String codeArticle, Model model, HttpServletRequest request) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        model.addAttribute("article", article);
        return "stock/sortie-saisie";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @GetMapping("/add")
    public String addArticlePage(Model model, HttpServletRequest request) {

        // Si l'article n'existe pas encore dans le modèle, en créer un nouveau
        if (!model.containsAttribute("article")) {
            model.addAttribute("article", new Article());
        }

        // Toujours ajouter les listes de données
        model.addAttribute("unites", udmService.getAllUdms());
        model.addAttribute("familles", familleService.getAllFamilles());

        return "article/article-saisie";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/save")
    public String insertArticle(@ModelAttribute("article") Article article,
                                @RequestParam(name = "udm") String udmId,
                                @RequestParam(name = "famille") String familleId,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {

        try {
            // Validation de base
            if (bindingResult.hasErrors()) {
                redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.article", bindingResult);
                redirectAttributes.addFlashAttribute("article", article);
                redirectAttributes.addFlashAttribute("ko", "Veuillez corriger les erreurs dans le formulaire");
                return "redirect:/article/add";
            }

            if (article.getDesignation() == null || article.getDesignation().trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "La désignation est obligatoire");
                redirectAttributes.addFlashAttribute("article", article);
                return "redirect:/article/add";
            }

            if (article.getSeuilMin() <= 0) {
                redirectAttributes.addFlashAttribute("ko", "Le seuil minimum doit être positif");
                redirectAttributes.addFlashAttribute("article", article);
                return "redirect:/article/add";
            }

            if (udmId == null || udmId.isBlank()) {
                throw new IllegalArgumentException("Unité de mesure obligatoire");
            }

            if (familleId == null || familleId.isBlank()) {
                throw new IllegalArgumentException("Famille obligatoire");
            }


            // Récupération des objets liés
            Udm udm = udmService.getUdmById(Integer.parseInt(udmId))
                    .orElseThrow(() -> new IllegalArgumentException("Unité de mesure obligatoire"));

            Famille famille = familleService.getFamilleById(Integer.parseInt(familleId))
                    .orElseThrow(() -> new IllegalArgumentException("Famille obligatoire"));

            // Génération du code
            article.setCodeArticle(idGenerator);
            article.setUdm(udm);
            article.setFamille(famille);

            // Sauvegarde
            Article savedArticle = articleService.saveArticle(article);

            // Message de succès
            redirectAttributes.addFlashAttribute("ok",
                    "Article '" + savedArticle.getDesignation() +
                            "' ajouté avec succès (Code: " + savedArticle.getCodeArticle() + ")");

            return "redirect:/article/list";

        } catch (IllegalArgumentException e) {
            // Pour les erreurs de validation, rediriger vers le formulaire
            redirectAttributes.addFlashAttribute("ko", "Erreur : " + e.getMessage());
            redirectAttributes.addFlashAttribute("article", article);
            return "redirect:/article/add";

        } catch (Exception e) {
            // Pour les autres erreurs
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de l'ajout de l'article: " + e.getMessage());
            redirectAttributes.addFlashAttribute("article", article);
            return "redirect:/article/add";
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/modifier")
    public String modifierArticle(@RequestParam(name = "codeArticle") String codeArticle,
                                  @RequestParam(name = "designation") String designation,
                                  @RequestParam(name = "idUdm") String idUdm,
                                  @RequestParam(name = "idFamille") String idFamille,
                                  @RequestParam(name = "seuilMinimum") String seuilMinimum,
                                  RedirectAttributes redirectAttributes) {
        try {
            // Validation des données
            if (codeArticle == null || codeArticle.trim().isEmpty()) {
                throw new IllegalArgumentException("Le code article est obligatoire");
            }

            if (designation == null || designation.trim().isEmpty()) {
                throw new IllegalArgumentException("La désignation est obligatoire");
            }

            if (seuilMinimum == null || seuilMinimum.trim().isEmpty()) {
                throw new IllegalArgumentException("Le seuil minimum est obligatoire");
            }

            // Appel du service de modification
            Article articleModifie = articleService.modifierArticle(
                    codeArticle.trim(),
                    designation.trim(),
                    seuilMinimum.trim(),
                    idUdm != null && !idUdm.isEmpty() ? idUdm : null,
                    idFamille != null && !idFamille.isEmpty() ? idFamille : null
            );

            // Message de succès
            redirectAttributes.addFlashAttribute("ok",
                    "✅ Article <strong>" + articleModifie.getCodeArticle() + "</strong> modifié avec succès !");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur de validation : " + e.getMessage());
            redirectAttributes.addFlashAttribute("codeArticle", codeArticle);
            redirectAttributes.addFlashAttribute("designation", designation);
            redirectAttributes.addFlashAttribute("seuilMinimum", seuilMinimum);
            redirectAttributes.addFlashAttribute("idUdm", idUdm);
            redirectAttributes.addFlashAttribute("idFamille", idFamille);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de la modification : " + e.getMessage());
            redirectAttributes.addFlashAttribute("codeArticle", codeArticle);
        }

        return "redirect:/article/list";
    }
}
