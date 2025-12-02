package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.VEtatStockService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
    @Autowired
    VEtatStockService vEtatStockService;

    @PostMapping("/save-entree")
    public String insertEntreeStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteEntree,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle);
        model.addAttribute("article", article);

        try {
            StockMere stockMere = new StockMere();

            StockFille stockFille = new StockFille();
            stockFille.setArticle(article);
            stockFille.setEntree(quantiteEntree);
            stockFille.setStockMere(stockMere);

            stockMereService.insertStockMere(stockMere);
            stockFilleService.insertStockFille(stockFille);
            
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Quantité d'entrée invalide.");
            model.addAttribute("quantiteEntree", quantiteEntree);
            return "stock/entree-saisie";
        }


        return "redirect:/admin/stock/etat-stock";
    }

    @PostMapping("/save-sortie")
    public String insertSortieStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteSortie,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle);
        model.addAttribute("article", article);

        try {
            double qSortie = Double.parseDouble(quantiteSortie);
            VEtatStock etat = vEtatStockService.getEtatStockByCode(codeArticle);
            double stockDisponible = Double.parseDouble(etat.getStockDisponible());

            if (qSortie > stockDisponible) {
                model.addAttribute("error", "La quantité de sortie dépasse le stock disponible. Quantité disponible: " + stockDisponible);
                model.addAttribute("quantiteSortie", quantiteSortie);
                return "stock/sortie-saisie";
            }

            StockMere stockMere = new StockMere();
            StockFille stockFille = new StockFille();
            stockFille.setArticle(article);
            stockFille.setSortie(quantiteSortie); // ici le setter peut lancer IllegalArgumentException
            stockFille.setStockMere(stockMere);

            stockMereService.insertStockMere(stockMere);
            stockFilleService.insertStockFille(stockFille);

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Quantité de sortie invalide.");
            model.addAttribute("quantiteSortie", quantiteSortie);
            return "stock/sortie-saisie";
        }

        return "redirect:/admin/stock/etat-stock";
    }

    @GetMapping("/etat-stock")
    public String getEtatStock(Model model) {
        VEtatStock[] etatStocks = vEtatStockService.getAllEtatStocks();
        model.addAttribute("etatStocks", etatStocks);
        return "stock/stock-liste";
    }
}
