package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.demande.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.demande.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.demande.bonSortie.BonSortieFilleService;
import afg.achat.afgApprovAchat.service.stock.StockFilleService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/bon-sortie")
public class BonSortieController {

    @Autowired DemandeMereService demandeMereService;
    @Autowired DemandeFilleService demandeFilleService;
    @Autowired UtilisateurService utilisateurService;
    @Autowired BonSortieMereRepo bsMereRepo;
    @Autowired BonSortieFilleRepo bsFilleRepo;
    @Autowired BonSortieFilleService bonSortieService;
    @Autowired StockFilleService stockFilleService;
    @Autowired IdGenerator idGenerator;

    // ─── Création depuis demande validée ────────────────────────────────────
    @GetMapping("/create-from-demande/{demandeId}")
    public String createFromDemande(@PathVariable String demandeId,
                                    RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = hasRole(auth, "ROLE_ADMIN");
        boolean isMG    = hasRole(auth, "ROLE_MOYENS_GENERAUX");

        if (!isAdmin && !isMG) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return "redirect:/demande/fiche/" + demandeId;
        }

        DemandeMere demande = demandeMereService.getDemandeMereById(demandeId).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }
        if (demande.getStatut() != StatutDemande.VALIDE) {
            redirectAttributes.addFlashAttribute("ko", "La demande n'est pas validée par le SG.");
            return "redirect:/demande/fiche/" + demandeId;
        }

        // Anti-doublon : bloquer si BS déjà existant (peu importe le statut)
        BonSortieMere existing = bsMereRepo
                .findFirstByDemandeMereOrderByIdDesc(demande).orElse(null);

        if (existing != null) {
            if (existing.getStatut() == BonSortieMere.Statut.CREE
                    || existing.getStatut() == BonSortieMere.Statut.PARTIELLE) {
                // Réouvrir le BS en cours
                return "redirect:/bon-sortie/fiche/" + existing.getId();
            }
            if (existing.getStatut() == BonSortieMere.Statut.VALIDEE) {
                redirectAttributes.addFlashAttribute("ko",
                        "Un bon de sortie a déjà été confirmé pour cette demande.");
                return "redirect:/demande/fiche/" + demandeId;
            }
        }

        // Lignes validées de la demande
        List<DemandeFille> lignesValidees =
                demandeFilleService.getDemandeFilleValideeByDemandeMere(demande);

        if (lignesValidees == null || lignesValidees.isEmpty()) {
            redirectAttributes.addFlashAttribute("ko",
                    "Aucune ligne d'article dans la demande.");
            return "redirect:/demande/fiche/" + demandeId;
        }

        // Créer BS mère
        BonSortieMere bs = new BonSortieMere();
        bs.setId(idGenerator);
        bs.setDemandeMere(demande);
        bs.setStatut(BonSortieMere.Statut.CREE);
        bs = bsMereRepo.save(bs);

        // Créer lignes BS
        for (DemandeFille l : lignesValidees) {
            if (l.getArticle() == null || l.getQuantite() <= 0) continue;

            BonSortieFille bsf = new BonSortieFille();
            bsf.setBonSortieMere(bs);
            bsf.setArticle(l.getArticle());
            bsf.setQuantiteDemandee(l.getQuantite());
            bsf.setQuantiteSortie(0);
            bsf.setStatut(BonSortieFille.Statut.EN_ATTENTE);
            bsFilleRepo.save(bsf);
        }

        redirectAttributes.addFlashAttribute("ok", "Bon de sortie créé.");
        return "redirect:/bon-sortie/fiche/" + bs.getId();
    }

    // ─── Fiche BS ────────────────────────────────────────────────────────────
    @GetMapping("/fiche/{bsId}")
    public String fiche(@PathVariable String bsId,
                        Model model,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasRole(auth, "ROLE_ADMIN") && !hasRole(auth, "ROLE_MOYENS_GENERAUX")) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return "redirect:/demande/list";
        }

        BonSortieMere bs = bsMereRepo.findById(bsId).orElse(null);
        if (bs == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de sortie introuvable.");
            return "redirect:/demande/list";
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);

        // Stock dispo + maxSortie par ligne (transient, pas persisté)
        for (BonSortieFille l : lignes) {
            double dispo = stockFilleService
                    .getStockDisponible(l.getArticle().getCodeArticle());
            l.setMaxSortie(Math.min(dispo, l.getQuantiteDemandee()));
        }

        // Map stock dispo pour l'affichage Thymeleaf
        Map<Integer, Double> dispoByArticleId = new HashMap<>();
        for (BonSortieFille l : lignes) {
            double dispo = stockFilleService
                    .getStockDisponible(l.getArticle().getCodeArticle());
            dispoByArticleId.put(l.getArticle().getId(), dispo);
        }

        BonSortieMere.Statut statut = bs.getStatut();

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("bs", bs);
        model.addAttribute("lignes", lignes);
        model.addAttribute("dispoByArticleId", dispoByArticleId);
        model.addAttribute("isCree",      statut == BonSortieMere.Statut.CREE);
        model.addAttribute("isPartielle", statut == BonSortieMere.Statut.PARTIELLE);
        model.addAttribute("isValidee",   statut == BonSortieMere.Statut.VALIDEE);

        return "bonSortie/bs-fiche";
    }

    // ─── Sauvegarder les quantités (sans impact stock) ───────────────────────
    @PostMapping("/fiche/{bsId}/save")
    public String saveQuantites(@PathVariable String bsId,
                                @RequestParam("lineId[]") List<Integer> lineIds,
                                @RequestParam("qteSortie[]") List<String> qtesSortie,
                                RedirectAttributes redirectAttributes) {

        BonSortieMere bs = bsMereRepo.findById(bsId)
                .orElseThrow(() -> new IllegalArgumentException("BS introuvable"));

        // Accepter aussi PARTIELLE (re-saisie possible)
        if (bs.getStatut() == BonSortieMere.Statut.VALIDEE) {
            redirectAttributes.addFlashAttribute("ko", "Ce BS est déjà validé.");
            return "redirect:/bon-sortie/fiche/" + bsId;
        }

        if (lineIds.size() != qtesSortie.size()) {
            redirectAttributes.addFlashAttribute("ko", "Données invalides.");
            return "redirect:/bon-sortie/fiche/" + bsId;
        }

        for (int i = 0; i < lineIds.size(); i++) {
            int lineId = lineIds.get(i);
            double qte = parseDoubleSafe(qtesSortie.get(i));

            BonSortieFille line = bsFilleRepo.findById(lineId)
                    .orElseThrow(() -> new IllegalArgumentException("Ligne BS introuvable"));

            if (!line.getBonSortieMere().getId().equals(bsId)) {
                redirectAttributes.addFlashAttribute("ko", "Ligne invalide.");
                return "redirect:/bon-sortie/fiche/" + bsId;
            }

            if (qte < 0) {
                redirectAttributes.addFlashAttribute("ko", "Quantité négative interdite.");
                return "redirect:/bon-sortie/fiche/" + bsId;
            }

            //Validation stock côté serveur
            double dispo = stockFilleService
                    .getStockDisponible(line.getArticle().getCodeArticle());
            double maxSortie = Math.min(dispo, line.getQuantiteDemandee());

            if (qte > maxSortie) {
                redirectAttributes.addFlashAttribute("ko",
                        "Quantité saisie dépasse le disponible pour : "
                                + line.getArticle().getDesignation()
                                + " (max: " + maxSortie + ")");
                return "redirect:/bon-sortie/fiche/" + bsId;
            }

            line.setQuantiteSortie(qte);
            bsFilleRepo.save(line);
        }

        redirectAttributes.addFlashAttribute("ok", "Quantités enregistrées.");
        return "redirect:/bon-sortie/fiche/" + bsId;
    }

    // ─── Confirmer la sortie ─────────────────────────────────────────────────
    @PostMapping("/fiche/{bsId}/confirm")
    public String confirm(@PathVariable String bsId,
                          RedirectAttributes redirectAttributes) {
        try {
            bonSortieService.confirmerSortie(bsId);
            redirectAttributes.addFlashAttribute("ok",
                    "Sortie confirmée. Stock mis à jour.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-sortie/fiche/" + bsId;
    }

    // ─── Helper ──────────────────────────────────────────────────────────────
    private boolean hasRole(
            org.springframework.security.core.Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private double parseDoubleSafe(String s) {
        try {
            if (s == null || s.isBlank()) return 0.0;
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}