package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.util.UdmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
    public String insertArticle(Model model) {
        return "article/article-liste";
    }
}
