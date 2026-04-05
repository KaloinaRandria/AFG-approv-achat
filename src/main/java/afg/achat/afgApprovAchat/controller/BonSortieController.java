package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.service.BonSortieService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/bon-sortie")
@RequiredArgsConstructor
public class BonSortieController {
    private final BonSortieService bonSortieService;
    private final DemandeMereService demandeMereService;
    private final DemandeFilleService demandeFilleService;
    private final BonSortieMereRepo bsMereRepo;
    private final BonSortieFilleRepo bsFilleRepo;

    // ── Créer un BS depuis la fiche demande ─────────────────────────────────
    @PostMapping("/creer/{idDemande}")
    public String creerBonSortie(@PathVariable("idDemande") String idDemande,
                                 @RequestParam("commentaire") String commentaire,
                                 @RequestParam("lignes[]") List<Integer> demandeFilleIds,
                                 @RequestParam("quantites[]") List<Double> quantites,
                                 RedirectAttributes redirectAttributes) {
        try {
            Utilisateur mg = (Utilisateur) SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();

            DemandeMere demande = demandeMereService.getDemandeMereById(idDemande)
                    .orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));

            if (demande.getStatut() != StatutDemande.VALIDE) {
                redirectAttributes.addFlashAttribute("ko",
                        "Un bon de sortie ne peut être créé que sur une demande validée.");
                return "redirect:/demande/fiche/" + idDemande;
            }

            if (demandeFilleIds == null || demandeFilleIds.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko",
                        "Veuillez sélectionner au moins un article.");
                return "redirect:/demande/fiche/" + idDemande;
            }

            // Créer le brouillon
            BonSortieMere bs = bonSortieService.creerBrouillon(demande, mg);
            bs.setCommentaire(commentaire);
            bsMereRepo.save(bs);

            // Ajouter les lignes
            for (int i = 0; i < demandeFilleIds.size(); i++) {
                int dfId       = demandeFilleIds.get(i);
                double quantite = quantites.get(i);
                if (quantite <= 0) continue;

                DemandeFille df = demandeFilleService.getDemandeFilleById(dfId);

                bonSortieService.ajouterLigne(bs, df, quantite);
            }

            // Confirmer directement (brouillon + confirmation en une passe)
            bonSortieService.confirmerBonSortie(bs.getId());

            redirectAttributes.addFlashAttribute("ok",
                    "Bon de sortie " + bs.getNumeroBs() + " créé et confirmé.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }

        return "redirect:/demande/fiche/" + idDemande;
    }

    // ── Fiche d'un BS (impression / aperçu) ─────────────────────────────────
    @GetMapping("/{id}")
    public String ficheBonSortie(@PathVariable("id") int id, Model model,
                                 RedirectAttributes redirectAttributes) {
        BonSortieMere bs = bsMereRepo.findById(id).orElse(null);
        if (bs == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de sortie introuvable.");
            return "redirect:/demande/list";
        }

        List<BonSortieFille> lignes = bsFilleRepo.findByBonSortieMere(bs);

        double totalBs = lignes.stream()
                .mapToDouble(BonSortieFille::getMontantTotal)
                .sum();

        model.addAttribute("bs", bs);
        model.addAttribute("lignes", lignes);
        model.addAttribute("totalBs", totalBs);

        return "bonSortie/bs-fiche";
    }
}
