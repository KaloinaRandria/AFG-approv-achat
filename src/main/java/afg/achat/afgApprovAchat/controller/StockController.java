package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.DTO.EtatStockAlerteDTO;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

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

            @RequestParam(required = false) String code,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String udm,
            @RequestParam(required = false) String etat, // RUPTURE / SEUIL / NORMAL

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "codeArticle") String sort,
            @RequestParam(defaultValue = "asc") String dir
    ) {
        // sécurité sort (évite PropertyReferenceException)
        if (!Set.of("codeArticle", "designation", "stockDisponible", "seuilMin").contains(sort)) {
            sort = "codeArticle";
        }

        Pageable pageable = PageRequest.of(page, size);
        // ✅ Recherche multi-critère
        Page<VEtatStock> etatPage = vEtatStockService.searchEtatStockMulti(code, designation, udm, etat, pageable);

        // ✅ codes page courante
        List<String> codes = etatPage.getContent().stream()
                .map(VEtatStock::getCodeArticle)
                .toList();

        // ✅ alertes map uniquement pour la colonne Etat
        Map<String, StockAlerte> alertesMapPage = stockAlerteService.getAlertesMapForCodes(codes);

        // ✅ conversion DTO
        List<EtatStockAlerteDTO> dtoContent = etatPage.getContent().stream().map(etatStock -> {
            EtatStockAlerteDTO dto = new EtatStockAlerteDTO(etatStock);
            StockAlerte alerte = alertesMapPage.get(etatStock.getCodeArticle());
            if (alerte != null) dto.setAlerte(alerte);
            return dto;
        }).toList();

        Page<EtatStockAlerteDTO> etatStocks = new PageImpl<>(dtoContent, pageable, etatPage.getTotalElements());

        // ✅ badge + modal alertes (inchangé)
        int alertesCount = stockAlerteService.getAlertesCount();
        List<StockAlerte> alertes = stockAlerteService.getAlertesAll();
        long ruptureCount = alertes.stream().filter(a -> "RUPTURE".equals(a.getTypeAlerte())).count();
        long seuilCount   = alertes.stream().filter(a -> "SEUIL".equals(a.getTypeAlerte())).count();

        model.addAttribute("etatStocks", etatStocks);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);

        // ✅ pour garder les valeurs dans les inputs
        model.addAttribute("code", code == null ? "" : code);
        model.addAttribute("designation", designation == null ? "" : designation);
        model.addAttribute("udm", udm == null ? "" : udm);
        model.addAttribute("etat", etat == null ? "" : etat);

        model.addAttribute("alertesCount", alertesCount);
        model.addAttribute("alertes", alertes);
        model.addAttribute("ruptureCount", ruptureCount);
        model.addAttribute("seuilCount", seuilCount);

        return "stock/stock-liste";
    }




}
