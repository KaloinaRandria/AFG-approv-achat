package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/article")
public class ArticleController {
    @Autowired
    ArticleService articleService;

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
}
