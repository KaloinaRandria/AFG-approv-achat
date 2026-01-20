package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.EtatStockAlerteDTO;
import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.VEtatStockService;
import afg.achat.afgApprovAchat.service.stock.StockAlerteService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock")
public class StockController {
    @Autowired
    StockMereService stockMereService;
    @Autowired
    StockFilleService stockFilleService;
    @Autowired
    ArticleService articleService;
    @Autowired
    VEtatStockService vEtatStockService;
    @Autowired
    StockAlerteService stockAlerteService;

    @PostMapping("/save-entree")
    public String insertEntreeStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteEntree,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
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


        return "redirect:/stock/etat-stock";
    }

    @PostMapping("/save-sortie")
    public String insertSortieStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteSortie,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
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

        return "redirect:/stock/etat-stock";
    }

    @GetMapping("/etat-stock")
    public String getEtatStock(
            Model model,
            HttpServletRequest request,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "codeArticle") String sort,
            @RequestParam(value = "dir", defaultValue = "asc") String dir,

            // pagination du modal notifications
            @RequestParam(value = "apage", defaultValue = "0") int apage,
            @RequestParam(value = "asize", defaultValue = "10") int asize
    ) {
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        // ✅ 1) Table stock pageable
        Page<VEtatStock> etatPage = vEtatStockService.getEtatStocksPage(q, pageable);

        // ✅ 2) codes page courante
        List<String> codes = etatPage.getContent().stream()
                .map(VEtatStock::getCodeArticle)
                .toList();

        // ✅ 3) alertes uniquement pour ces codes
        Map<String, StockAlerte> alertesMapPage = stockAlerteService.getAlertesMapForCodes(codes);

        // ✅ 4) conversion DTO pour la page
        List<EtatStockAlerteDTO> dtoContent = etatPage.getContent().stream().map(etat -> {
            EtatStockAlerteDTO dto = new EtatStockAlerteDTO(etat);
            StockAlerte alerte = alertesMapPage.get(etat.getCodeArticle());
            if (alerte != null) dto.setAlerte(alerte);
            return dto;
        }).toList();

        Page<EtatStockAlerteDTO> etatStocks = new PageImpl<>(dtoContent, pageable, etatPage.getTotalElements());

        // ✅ badge count total
        int alertesCount = stockAlerteService.getAlertesCount();

        // ✅ modal alertes pageable
        Pageable alertPageable = PageRequest.of(apage, asize, Sort.by(Sort.Direction.DESC, "codeArticle"));
        Page<StockAlerte> alertesPage = stockAlerteService.getAlertesPage(alertPageable);

        // ✅ counts (sur la page du modal — si tu veux le total global, il faut une requête count par type)
        long ruptureCount = alertesPage.getContent().stream().filter(a -> "RUPTURE".equals(a.getTypeAlerte())).count();
        long seuilCount = alertesPage.getContent().stream().filter(a -> "SEUIL".equals(a.getTypeAlerte())).count();

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("etatStocks", etatStocks);

        model.addAttribute("q", q);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);

        model.addAttribute("alertesCount", alertesCount);

        // modal
        model.addAttribute("alertesPage", alertesPage);
        model.addAttribute("apage", apage);
        model.addAttribute("asize", asize);
        model.addAttribute("ruptureCount", ruptureCount);
        model.addAttribute("seuilCount", seuilCount);

        return "stock/stock-liste";
    }


}
