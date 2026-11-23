package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.bonSortie.BonSortieFille;
import afg.achat.afgApprovAchat.model.bonSortie.BonSortieMere;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieFilleRepo;
import afg.achat.afgApprovAchat.repository.bonSortie.BonSortieMereRepo;
import afg.achat.afgApprovAchat.service.BonSortieService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bon-sortie")
@RequiredArgsConstructor
public class BonSortieController {
    /** Préfixe des champs de quantité du formulaire : qte_&lt;id de la ligne de demande&gt;. */
    static final String PREFIXE_QUANTITE = "qte_";

    private final BonSortieService bonSortieService;
    private final DemandeMereService demandeMereService;
    private final BonSortieMereRepo bsMereRepo;
    private final BonSortieFilleRepo bsFilleRepo;
    private final WorkflowService workflowService;
    private final UtilisateurService utilisateurService;
    private final afg.achat.afgApprovAchat.service.SortieAFaireService sortieAFaireService;

    // ── Bons de sortie à faire ──────────────────────────────────────────────
    /**
     * Demandes de matériel validées dont des articles restent à sortir : celles dont le stock est
     * disponible (prêtes) d'abord, puis celles qui attendent une livraison.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @GetMapping("/a-faire")
    public String aFaire(Model model) {
        List<afg.achat.afgApprovAchat.service.SortieAFaireService.DemandeASortir> demandes = sortieAFaireService.demandes();
        model.addAttribute("pretes", demandes.stream().filter(d -> d.prete()).toList());
        model.addAttribute("enAttente", demandes.stream().filter(d -> !d.prete()).toList());
        return "bonSortie/bs-a-faire";
    }

    // ── Créer un BS depuis la fiche demande ─────────────────────────────────
    // Même condition que l'affichage du bouton sur la fiche (canCreateBS : rôle MG)
    @PreAuthorize("hasRole('MOYENS_GENERAUX')")
    @PostMapping("/creer/{idDemande}")
    public String creerBonSortie(@PathVariable String idDemande,
                                 @RequestParam(name = "commentaire", required = false) String commentaire,
                                 @RequestParam Map<String, String> parametres,
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

            if (!demande.getTypeDemandeEffectif().isArticlesEnStock()) {
                redirectAttributes.addFlashAttribute("ko",
                        "Un bon de sortie ne concerne que les demandes de matériel (articles en stock).");
                return "redirect:/demande/fiche/" + idDemande;
            }

            // Quantités envoyées sous la forme qte_<id de la ligne de demande> : chaque
            // quantité reste attachée à sa ligne, même si des champs ne sont pas envoyés
            // (lignes sans stock, champ désactivé).
            Map<Integer, Double> quantitesParLigne = new LinkedHashMap<>();
            for (Map.Entry<String, String> p : parametres.entrySet()) {
                if (!p.getKey().startsWith(PREFIXE_QUANTITE)) continue;
                String valeur = p.getValue() == null ? "" : p.getValue().trim().replace(',', '.');
                if (valeur.isEmpty()) continue;
                try {
                    quantitesParLigne.put(Integer.parseInt(p.getKey().substring(PREFIXE_QUANTITE.length())),
                            Double.parseDouble(valeur));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Quantité invalide : " + p.getValue());
                }
            }

            // Création + confirmation en une seule transaction : tout ou rien
            BonSortieMere bs = bonSortieService.creerEtConfirmer(demande, mg, commentaire, quantitesParLigne);

            redirectAttributes.addFlashAttribute("ok",
                    "Bon de sortie " + bs.getNumeroBs() + " créé et confirmé.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }

        return "redirect:/demande/fiche/" + idDemande;
    }

    // ── Annuler un BS confirmé (remise en stock) ────────────────────────────
    // Mêmes droits que la création ; le motif est obligatoire
    @PreAuthorize("hasRole('MOYENS_GENERAUX')")
    @PostMapping("/{id}/annuler")
    public String annulerBonSortie(@PathVariable("id") int id,
                                   @RequestParam(name = "motif", required = false) String motif,
                                   RedirectAttributes redirectAttributes) {
        try {
            Utilisateur auteur = (Utilisateur) SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            BonSortieMere bs = bonSortieService.annulerBonSortie(id, auteur, motif);
            redirectAttributes.addFlashAttribute("ok",
                    "Bon de sortie " + bs.getNumeroBs() + " annulé : les quantités sont remises en stock.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-sortie/" + id;
    }

    // ── Liste de tous les bons de sortie (Moyens Généraux) ──────────────────
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @GetMapping("/list")
    public String listeBonsSortie(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String statut,
                                  @RequestParam(required = false) String numero,
                                  @RequestParam(required = false) String demandeur,
                                  @RequestParam(required = false) String article,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  HttpServletRequest request,
                                  Model model) {
        BonSortieMere.StatutBonSortie statutFiltre = null;
        if (statut != null && !statut.isBlank()) {
            try {
                statutFiltre = BonSortieMere.StatutBonSortie.valueOf(statut.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // statut inconnu : pas de filtre
            }
        }
        int taille = Math.min(Math.max(size, 5), 100);

        Page<BonSortieMere> bons = bonSortieService.rechercher(statutFiltre, dateFrom, dateTo,
                numero, demandeur, article, PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("bons", bons);
        model.addAttribute("totaux", bonSortieService.totauxParBon(bons.getContent()));
        model.addAttribute("statuts", BonSortieService.STATUTS_LISTE);
        model.addAttribute("statut", statutFiltre == null || !BonSortieService.STATUTS_LISTE.contains(statutFiltre)
                ? "" : statutFiltre.name());
        model.addAttribute("numero", numero == null ? "" : numero);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("article", article == null ? "" : article);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        return "bonSortie/bs-liste";
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

        // Même règle de consultation que la fiche de la demande liée
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur current = utilisateurService.getUtilisateurByMail(
                ((Utilisateur) auth.getPrincipal()).getMail());
        if (bs.getDemandeMere() == null
                || !workflowService.aAccesDemande(bs.getDemandeMere(), current, auth)) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé à ce bon de sortie.");
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
