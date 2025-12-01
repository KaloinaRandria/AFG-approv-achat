package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/stock")
public class StockController {
    @Autowired
    StockMereService stockMereService;
    @Autowired
    StockFilleService stockFilleService;
    @Autowired
    ArticleService articleService;

    @PostMapping("/save-entree")
    public String insertEntreeStock(@RequestParam(name = "codeArticle") String codeArticle,@RequestParam(name = "quantite") String quantiteEntree) {
        Article article = articleService.getArticleByCodeArticle(codeArticle);

        StockMere stockMere = new StockMere();

        StockFille stockFille = new StockFille();
        stockFille.setArticle(article);
        stockFille.setEntree(quantiteEntree);
        stockFille.setStockMere(stockMere);

        stockMereService.insertStockMere(stockMere);
        stockFilleService.insertStockFille(stockFille);

        return "stock/stock-liste";
    }
}
