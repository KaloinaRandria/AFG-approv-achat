package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonFille;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonFilleService;
import afg.achat.afgApprovAchat.service.bonlivraison.BonLivraisonMereService;
import afg.achat.afgApprovAchat.service.bonlivraison.LigneLivraisonAffichee;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Bons de livraison : liste et fiche. Un BL se crée en réceptionnant un bon de commande
 * ({@link ReceptionBonCommandeController}).
 */
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bonlivraison")
public class BonLivraisonController {
    @Autowired
    DeviseService deviseService;
    @Autowired
    BonLivraisonMereService bonLivraisonMereService;
    @Autowired
    BonLivraisonFilleService bonLivraisonFilleService;


    /**
     * Saisie directe d'un BL (sans bon de commande) : désactivée. Un bon de livraison se crée
     * en réceptionnant un bon de commande validé ({@link ReceptionBonCommandeController}).
     * Les anciennes adresses redirigent vers la liste, y compris un formulaire déjà ouvert.
     */
    @RequestMapping({"/add", "/save"})
    public String saisieDirecteDesactivee(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("ko", "La saisie directe d'un bon de livraison est désactivée : "
                + "réceptionnez le bon de commande depuis sa fiche (bouton « Réceptionner »).");
        return "redirect:/bonlivraison/list";
    }

    // ── Fiche ────────────────────────────────────────────────────────────────

    @GetMapping("/fiche/{id}")
    public String ficheBonLivraison(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        BonLivraisonMere bl = bonLivraisonMereService.getBonLivraisonMereById(id).orElse(null);
        if (bl == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de livraison introuvable.");
            return "redirect:/bonlivraison/list";
        }
        List<BonLivraisonFille> lignes = bonLivraisonFilleService.getBonLivraisonFillesByMereId(id);
        double totalLignes = lignes.stream().mapToDouble(l -> l.getQuantiteRecu() * l.getPrixUnitaire()).sum();

        model.addAttribute("bl", bl);
        // Lignes de même article et même prix fusionnées (BC groupé : une ligne par demande en base)
        model.addAttribute("lignes", LigneLivraisonAffichee.regrouper(lignes));
        // Total enregistré sur le BL ; à défaut, recalculé depuis les lignes
        model.addAttribute("total", bl.getTotalPrix() != null ? bl.getTotalPrix() : totalLignes);
        return "bl/bl-fiche";
    }

    @GetMapping("/list")
    public String bonLivraisonMereListe(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String numero,
                                        @RequestParam(required = false) String fournisseur,
                                        @RequestParam(required = false) String bonCommande,
                                        @RequestParam(required = false) String article,
                                        @RequestParam(required = false) String devise,
                                        @RequestParam(required = false) String origine,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                        HttpServletRequest request,
                                        Model model) {
        Integer deviseId = null;
        if (devise != null && !devise.isBlank()) {
            try {
                deviseId = Integer.parseInt(devise.trim());
            } catch (NumberFormatException ignored) {
                // devise inconnue : pas de filtre
            }
        }
        BonLivraisonMereService.Origine origineFiltre = null;
        if (origine != null && !origine.isBlank()) {
            try {
                origineFiltre = BonLivraisonMereService.Origine.valueOf(origine.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // origine inconnue : pas de filtre
            }
        }

        int taille = Math.min(Math.max(size, 5), 100);
        Page<BonLivraisonMere> bons = bonLivraisonMereService.rechercher(new BonLivraisonMereService.Recherche(
                        numero, fournisseur, bonCommande, article, deviseId, origineFiltre, dateFrom, dateTo),
                PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("bons", bons);
        model.addAttribute("devises", deviseService.getAllDevises());
        model.addAttribute("numero", numero == null ? "" : numero);
        model.addAttribute("fournisseur", fournisseur == null ? "" : fournisseur);
        model.addAttribute("bonCommande", bonCommande == null ? "" : bonCommande);
        model.addAttribute("article", article == null ? "" : article);
        model.addAttribute("devise", deviseId == null ? "" : String.valueOf(deviseId));
        model.addAttribute("origine", origineFiltre == null ? "" : origineFiltre.name());
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        return "bl/bl-liste";
    }


}
