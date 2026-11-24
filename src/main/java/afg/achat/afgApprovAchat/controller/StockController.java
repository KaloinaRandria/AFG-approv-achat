package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.DTO.EtatStockAlerteDTO;
import afg.achat.afgApprovAchat.model.VEtatStock;
import afg.achat.afgApprovAchat.model.stock.StockAlerte;
import afg.achat.afgApprovAchat.model.stock.StockFille;
import afg.achat.afgApprovAchat.model.stock.StockMere;
import afg.achat.afgApprovAchat.model.Famille;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.VEtatStockService;
import afg.achat.afgApprovAchat.service.stock.StockAlerteService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.stock.StockMereService;
import afg.achat.afgApprovAchat.service.stock.MouvementStockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Autowired
    FamilleService familleService;
    @Autowired
    MouvementStockService mouvementStockService;
    public static final int FAMILLE_MATERIELS_INFORMATIQUE_ID = 19;


    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/save-entree")
    public String insertEntreeStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteEntree,
                                    @RequestParam(name = "prixUnitaire", required = false) String prixUnitaire,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        model.addAttribute("article", article);
        model.addAttribute("quantiteEntree", quantiteEntree);
        model.addAttribute("prixUnitaire", prixUnitaire);

        // Un article inactif n'a plus de stock
        if (!article.estActif()) {
            model.addAttribute("error", "Cet article est inactif : il n'est plus géré en stock.");
            return "stock/entree-saisie";
        }

        try {
            double quantite = nombre(quantiteEntree);
            double prix = prixUnitaire == null || prixUnitaire.isBlank() ? 0.0 : nombre(prixUnitaire);
            // Lot FIFO + mouvement d'entrée : l'état de stock et le stock FIFO restent égaux
            mouvementStockService.entreeManuelle(article, quantite, prix);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e instanceof NumberFormatException
                    ? "Quantité ou prix invalide." : e.getMessage());
            return "stock/entree-saisie";
        }

        return "redirect:/stock/etat-stock";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/save-sortie")
    public String insertSortieStock(@RequestParam(name = "codeArticle") String codeArticle,
                                    @RequestParam(name = "quantite") String quantiteSortie,
                                    Model model) {
        Article article = articleService.getArticleByCodeArticle(codeArticle) .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        model.addAttribute("article", article);
        model.addAttribute("quantiteSortie", quantiteSortie);

        try {
            // Consommation FIFO des lots (contrôle du disponible inclus) + mouvement de sortie
            mouvementStockService.sortieManuelle(article, nombre(quantiteSortie));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e instanceof NumberFormatException
                    ? "Quantité de sortie invalide." : e.getMessage());
            return "stock/sortie-saisie";
        }

        return "redirect:/stock/etat-stock";
    }

    private static double nombre(String valeur) {
        if (valeur == null || valeur.isBlank()) throw new NumberFormatException("vide");
        return Double.parseDouble(valeur.trim().replace(',', '.'));
    }

    @GetMapping("/etat-stock")
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX','IT')")
    public String getEtatStock(
            Model model,
            HttpServletRequest request,

            @RequestParam(required = false) String code,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String udm,
            @RequestParam(required = false) String etat, // RUPTURE / SEUIL / NORMAL
            @RequestParam(required = false) String famille,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int taille = Math.min(Math.max(size, 5), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), taille);
        // Recherche multi-critère
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean hasFullStockAccess = hasRole(authentication, "ROLE_ADMIN")
                || hasRole(authentication, "ROLE_MOYENS_GENERAUX");
        // Accès complet : filtre de famille au choix ; sinon, seulement la famille autorisée
        Integer familleFiltre = null;
        if (hasFullStockAccess && famille != null && !famille.isBlank()) {
            try {
                familleFiltre = Integer.parseInt(famille.trim());
            } catch (NumberFormatException ignored) {
                // famille inconnue : pas de filtre
            }
        }
        // Pas de ternaire Integer/int ici : il déballerait familleFiltre (null = toutes) en int
        Integer familleVisibleId = familleFiltre;
        if (!hasFullStockAccess) {
            familleVisibleId = FAMILLE_MATERIELS_INFORMATIQUE_ID;
        }

        Page<VEtatStock> etatPage = vEtatStockService.searchEtatStockMulti(
                code, designation, udm, etat, familleVisibleId, pageable);

        // codes page courante
        List<String> codes = etatPage.getContent().stream()
                .map(VEtatStock::getCodeArticle)
                .toList();

        // alertes map uniquement pour la colonne Etat
        Map<String, StockAlerte> alertesMapPage = stockAlerteService.getAlertesMapForCodes(codes);

        // conversion DTO
        List<EtatStockAlerteDTO> dtoContent = etatPage.getContent().stream().map(etatStock -> {
            EtatStockAlerteDTO dto = new EtatStockAlerteDTO(etatStock);
            StockAlerte alerte = alertesMapPage.get(etatStock.getCodeArticle());
            if (alerte != null) dto.setAlerte(alerte);
            return dto;
        }).toList();

        Page<EtatStockAlerteDTO> etatStocks = new PageImpl<>(dtoContent, pageable, etatPage.getTotalElements());

        // badge + modal alertes : toutes les familles visibles, indépendamment du filtre
        int alertesCount = hasFullStockAccess
                ? stockAlerteService.getAlertesCount()
                : stockAlerteService.getAlertesCountByFamilleId(FAMILLE_MATERIELS_INFORMATIQUE_ID);
        List<StockAlerte> alertes = hasFullStockAccess
                ? stockAlerteService.getAlertesAll()
                : stockAlerteService.getAlertesAllByFamilleId(FAMILLE_MATERIELS_INFORMATIQUE_ID);
        long ruptureCount = alertes.stream().filter(a -> "RUPTURE".equals(a.getTypeAlerte())).count();
        long seuilCount   = alertes.stream().filter(a -> "SEUIL".equals(a.getTypeAlerte())).count();

        model.addAttribute("etatStocks", etatStocks);

        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));

        // pour garder les valeurs dans les inputs
        model.addAttribute("code", code == null ? "" : code);
        model.addAttribute("designation", designation == null ? "" : designation);
        model.addAttribute("udm", udm == null ? "" : udm);
        model.addAttribute("etat", etat == null ? "" : etat.trim().toUpperCase());
        model.addAttribute("famille", familleFiltre == null ? "" : String.valueOf(familleFiltre));
        model.addAttribute("peutFiltrerFamille", hasFullStockAccess);
        model.addAttribute("familles", hasFullStockAccess ? familleService.getAllFamilles() : new Famille[0]);

        model.addAttribute("alertesCount", alertesCount);
        model.addAttribute("alertes", alertes);
        model.addAttribute("ruptureCount", ruptureCount);
        model.addAttribute("seuilCount", seuilCount);

        return "stock/stock-liste";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }





}
