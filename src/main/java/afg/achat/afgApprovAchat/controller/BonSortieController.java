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

    // ✅ Création automatique depuis une demande validée (SG)
    @GetMapping("/create-from-demande/{demandeId}")
    public String createFromDemande(@PathVariable("demandeId") String demandeId,
                                    RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isMG = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

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
            redirectAttributes.addFlashAttribute("ko", "La demande n'est pas encore validée par le SG.");
            return "redirect:/demande/fiche/" + demandeId;
        }

        //éviter doublon: si un BS existe déjà pour cette demande et est CREE, on le réutilise
        BonSortieMere existing = bsMereRepo.findFirstByDemandeMereOrderByIdDesc(demande).orElse(null);
        if (existing != null && existing.getStatut() == BonSortieMere.Statut.CREE) {
            return "redirect:/bon-sortie/fiche/" + existing.getId();
        }

        // 1) créer BS mère
        BonSortieMere bs = new BonSortieMere();
        bs.setId(idGenerator);
        bs.setDemandeMere(demande);
        bs.setStatut(BonSortieMere.Statut.CREE);
        bs = bsMereRepo.save(bs);

        // 2) créer lignes BS depuis demandeFille
        List<DemandeFille> lignesDemande = demandeFilleService.getDemandeFilleByDemandeMere(demande);
        if (lignesDemande == null || lignesDemande.isEmpty()) {
            redirectAttributes.addFlashAttribute("ko", "Aucune ligne d'article dans la demande.");
            return "redirect:/demande/fiche/" + demandeId;
        }

        for (DemandeFille l : lignesDemande) {
            if (l.getArticle() == null) continue;
            if (l.getQuantite() <= 0) continue;

            BonSortieFille bsf = new BonSortieFille();
            bsf.setBonSortieMere(bs);
            bsf.setArticle(l.getArticle());
            bsf.setQuantiteDemandee(l.getQuantite());
            bsf.setQuantiteSortie(0); // sera saisi par MG
            bsFilleRepo.save(bsf);
        }

        redirectAttributes.addFlashAttribute("ok", "Bon de sortie créé.");
        return "redirect:/bon-sortie/fiche/" + bs.getId();
    }

    // ✅ Fiche BS
    @GetMapping("/fiche/{bsId}")
    public String fiche(@PathVariable("bsId") String bsId,
                        Model model,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isMG = auth.getAuthorities().stream().anyMatch(a -> "ROLE_MOYENS_GENERAUX".equals(a.getAuthority()));

        if (!isAdmin && !isMG) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return "redirect:/demande/list";
        }

        BonSortieMere bs = bsMereRepo.findById(bsId).orElse(null);
        if (bs == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de sortie introuvable.");
            return "redirect:/demande/list";
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);

        // Stock dispo par article (pour l’affichage)
        Map<Integer, Double> dispoByArticleId = new HashMap<>();
        for (BonSortieFille l : lignes) {
            String code = l.getArticle().getCodeArticle();
            double dispo = stockFilleService.getStockDisponible(code);
            dispoByArticleId.put(l.getArticle().getId(), dispo);
        }

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("bs", bs);
        model.addAttribute("lignes", lignes);
        model.addAttribute("dispoByArticleId", dispoByArticleId);

        model.addAttribute("isCree", bs.getStatut() == BonSortieMere.Statut.CREE);
        model.addAttribute("isValidee", bs.getStatut() == BonSortieMere.Statut.VALIDEE);

        return "bonSortie/bs-fiche";
    }

    // ✅ Sauvegarder les quantités saisies (sans impact stock)
    @PostMapping("/fiche/{bsId}/save")
    public String saveQuantites(@PathVariable("bsId") String bsId,
                                @RequestParam("lineId[]") List<Integer> lineIds,
                                @RequestParam("qteSortie[]") List<String> qtesSortie,
                                RedirectAttributes redirectAttributes) {

        BonSortieMere bs = bsMereRepo.findById(bsId)
                .orElseThrow(() -> new IllegalArgumentException("BS introuvable"));

        if (bs.getStatut() != BonSortieMere.Statut.CREE) {
            redirectAttributes.addFlashAttribute("ko", "BS déjà validé.");
            return "redirect:/bon-sortie/fiche/" + bsId;
        }

        if (lineIds.size() != qtesSortie.size()) {
            redirectAttributes.addFlashAttribute("ko", "Données invalides.");
            return "redirect:/bon-sortie/fiche/" + bsId;
        }

        for (int i = 0; i < lineIds.size(); i++) {
            Integer lineId = lineIds.get(i);
            double qte = parseDoubleSafe(qtesSortie.get(i));

            BonSortieFille line = bsFilleRepo.findById(lineId)
                    .orElseThrow(() -> new IllegalArgumentException("Ligne BS introuvable: " + lineId));

            if (!line.getBonSortieMere().getId().equals(bsId)) {
                throw new IllegalArgumentException("Ligne ne correspond pas à ce BS.");
            }

            if (qte < 0) throw new IllegalArgumentException("Quantité négative interdite");
            if (qte > line.getQuantiteDemandee()) throw new IllegalArgumentException("Quantité > demandée");

            line.setQuantiteSortie(qte);
            bsFilleRepo.save(line);
        }

        redirectAttributes.addFlashAttribute("ok", "Quantités enregistrées.");
        return "redirect:/bon-sortie/fiche/" + bsId;
    }

    // ✅ Confirmer (écrit en stock + passe BS à VALIDEE)
    @PostMapping("/fiche/{bsId}/confirm")
    public String confirm(@PathVariable("bsId") String bsId,
                          RedirectAttributes redirectAttributes) {
        try {
            bonSortieService.confirmerSortie(bsId);
            redirectAttributes.addFlashAttribute("ok", "Sortie confirmée. Stock mis à jour.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-sortie/fiche/" + bsId;
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
