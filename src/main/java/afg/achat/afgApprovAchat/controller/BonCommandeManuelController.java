package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.bonCommande.BesoinAchatService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeManuelService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.bonCommande.SaisieBonCommande;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bon de commande créé manuellement (sans demande d'achat) : saisie par un créateur
 * (MG_CREATOR), validation des Moyens Généraux (MG_VALIDATOR) puis du S.G.
 * Les règles sont dans {@link BonCommandeManuelService}.
 */
@Controller
@RequestMapping("/bon-commande/manuel")
@RequiredArgsConstructor
public class BonCommandeManuelController {

    private static final String ROLES_CREATION = "hasAnyRole('ADMIN','MG_CREATOR','MOYENS_GENERAUX')";
    private static final String ROLES_VALIDATION = "hasAnyRole('ADMIN','MG_VALIDATOR','SG')";

    private final BonCommandeManuelService bonCommandeManuelService;
    private final BonCommandeService bonCommandeService;
    private final FournisseurService fournisseurService;
    private final UtilisateurService utilisateurService;
    private final DemandeFilleService demandeFilleService;
    private final BesoinAchatService besoinAchatService;
    private final afg.achat.afgApprovAchat.service.util.DeviseService deviseService;

    // ── Saisie ───────────────────────────────────────────────────────────────

    /**
     * Nouveau bon de commande, vide ou pré-rempli avec des lignes de demandes à acheter
     * (sélectionnées dans l'écran « Lignes à acheter »).
     *
     * @param lignesDemande identifiants des lignes de demande à commander
     */
    @PreAuthorize(ROLES_CREATION)
    @GetMapping("/nouveau")
    public String nouveau(@RequestParam(name = "lignes", required = false) List<Integer> lignesDemande,
                          Model model) {
        BonCommandeMere bc = new BonCommandeMere();
        bc.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);

        if (lignesDemande != null && !lignesDemande.isEmpty()) {
            List<DemandeFille> selection = lignesDemande.stream()
                    .distinct()
                    .map(demandeFilleService::getDemandeFilleById)
                    .filter(Objects::nonNull)
                    // Lignes du même article côte à côte : elles seront fusionnées à l'impression
                    .sorted(Comparator.comparing((DemandeFille df) ->
                                    df.getArticle() != null ? df.getArticle().getCodeArticle() : "")
                            .thenComparing(df -> df.getDemandeMere().getId()))
                    .toList();
            // Un bon ne regroupe qu'une devise : celle des demandes choisies
            List<String> codes = selection.stream().map(df -> df.getDemandeMere().getCodeDevise()).distinct().toList();
            if (codes.size() > 1) {
                model.addAttribute("ko", "Les lignes choisies sont dans plusieurs devises (" + String.join(", ", codes)
                        + ") : un bon de commande ne regroupe qu'une devise. Sélectionnez des lignes de même devise.");
                selection = List.of();
            } else if (!selection.isEmpty()) {
                bc.appliquerDevise(selection.get(0).getDemandeMere().getDevise(), java.time.LocalDateTime.now());
            }
            // Une ligne par besoin, avec le reste à commander, le prix et l'unité de la demande
            for (BesoinAchatService.BesoinAchat besoin : besoinAchatService.besoins(selection)) {
                if (besoin.getResteACommander() <= 0) continue;
                DemandeFille df = besoin.getLigne();
                BonCommandeFille ligne = new BonCommandeFille();
                ligne.setDemandeFille(df);
                ligne.setArticle(df.getArticle());
                ligne.setDesignation(df.getArticle() != null ? df.getArticle().getDesignation() : "");
                ligne.setQuantiteCommandee(besoin.getResteACommander());
                ligne.setPrixUnitaireHT(df.getPrixUnitaire() != null ? df.getPrixUnitaire() : 0);
                bc.getLignes().add(ligne);
            }
            if (bc.getLignes().isEmpty() && !model.containsAttribute("ko")) {
                model.addAttribute("ko", "Les lignes sélectionnées n'ont plus rien à commander.");
            }
        }
        return formulaire(bc, model);
    }

    @PreAuthorize(ROLES_CREATION)
    @GetMapping("/{id}/modifier")
    public String modifier(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        BonCommandeMere bc = bonCommandeService.getBonCommandeMereById(id).orElse(null);
        if (bc == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de commande introuvable.");
            return "redirect:/bon-commande/list";
        }
        if (!BonCommandeManuelService.peutModifier(bc, auth())) {
            redirectAttributes.addFlashAttribute("ko", "Ce bon de commande ne peut plus être modifié.");
            return "redirect:/bon-commande/" + id;
        }
        return formulaire(bc, model);
    }

    private String formulaire(BonCommandeMere bc, Model model) {
        model.addAttribute("bonCommandeMere", bc);
        model.addAttribute("lignes", bc.getLignes());
        model.addAttribute("fournisseurs", fournisseurService.getAllFournisseurs());
        model.addAttribute("devises", deviseService.getAllDevises());
        return "bc/bon-commande-manuel-saisie";
    }

    @PreAuthorize(ROLES_CREATION)
    @PostMapping("/sauvegarder")
    public String sauvegarder(@RequestParam(required = false) String id,
                              @RequestParam(name = "fournisseur", required = false) String fournisseurId,
                              @RequestParam(required = false) String referenceFournisseur,
                              @RequestParam(required = false) String dateLivraisonPrevue,
                              @RequestParam(required = false) String lieuLivraison,
                              @RequestParam(required = false) String modalitePaiement,
                              @RequestParam(required = false) String referenceApprobation,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) Double tauxRemise,
                              @RequestParam(required = false) Double tauxTVA,
                              @RequestParam(name = "devise", required = false) String deviseId,
                              @RequestParam(name = "designation[]", required = false) List<String> designations,
                              @RequestParam(name = "codeArticle[]", required = false) List<String> codesArticle,
                              @RequestParam(name = "quantite[]", required = false) List<String> quantites,
                              @RequestParam(name = "unite[]", required = false) List<String> unites,
                              @RequestParam(name = "prixUnitaire[]", required = false) List<String> prixUnitaires,
                              @RequestParam(name = "demandeFilleId[]", required = false) List<String> demandeFilleIds,
                              RedirectAttributes redirectAttributes) {
        // Tous les champs d'une ligne sont toujours envoyés (aucun n'est désactivé) : les listes restent alignées
        List<SaisieBonCommande.Ligne> lignes = new ArrayList<>();
        int nombre = designations == null ? 0 : designations.size();
        for (int i = 0; i < nombre; i++) {
            lignes.add(new SaisieBonCommande.Ligne(designations.get(i), valeur(codesArticle, i),
                    valeur(quantites, i), valeur(unites, i), valeur(prixUnitaires, i), valeur(demandeFilleIds, i)));
        }
        SaisieBonCommande saisie = new SaisieBonCommande(id, fournisseurId, referenceFournisseur,
                dateLivraisonPrevue, lieuLivraison, modalitePaiement, referenceApprobation, description,
                tauxRemise, tauxTVA, lignes, deviseId);

        try {
            BonCommandeMere bc = bonCommandeManuelService.enregistrer(saisie, utilisateurCourant(), auth());
            redirectAttributes.addFlashAttribute("ok", "Bon de commande n° " + bc.getNumero()
                    + " enregistré en brouillon. Soumettez-le pour lancer la validation.");
            return "redirect:/bon-commande/" + bc.getId();
        } catch (WorkflowException e) {
            // La saisie n'est pas conservée ; pour un nouveau bon, les lignes de demande choisies le sont
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            if (id != null && !id.isBlank()) {
                return "redirect:/bon-commande/manuel/" + id + "/modifier";
            }
            String lignesChoisies = demandeFilleIds == null ? "" : demandeFilleIds.stream()
                    .filter(v -> v != null && v.matches("\\d+"))
                    .distinct()
                    .collect(Collectors.joining(","));
            return lignesChoisies.isEmpty()
                    ? "redirect:/bon-commande/manuel/nouveau"
                    : "redirect:/bon-commande/manuel/nouveau?lignes=" + lignesChoisies;
        }
    }

    // ── Circuit de validation ────────────────────────────────────────────────

    @PreAuthorize(ROLES_CREATION)
    @PostMapping("/{id}/soumettre")
    public String soumettre(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            bonCommandeManuelService.soumettre(id, utilisateurCourant(), auth());
            redirectAttributes.addFlashAttribute("ok", "Bon de commande soumis à la validation des Moyens Généraux.");
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + id;
    }

    @PreAuthorize(ROLES_CREATION)
    @PostMapping("/{id}/annuler")
    public String annuler(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            bonCommandeManuelService.annuler(id, auth());
            redirectAttributes.addFlashAttribute("ok", "Bon de commande annulé.");
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + id;
    }

    @PreAuthorize(ROLES_VALIDATION)
    @PostMapping("/{id}/valider")
    public String valider(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("ok",
                    bonCommandeManuelService.valider(id, utilisateurCourant(), auth()));
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + id;
    }

    @PreAuthorize(ROLES_VALIDATION)
    @PostMapping("/{id}/rejeter")
    public String rejeter(@PathVariable String id,
                          @RequestParam(required = false) String motif,
                          RedirectAttributes redirectAttributes) {
        try {
            bonCommandeManuelService.rejeter(id, utilisateurCourant(), auth(), motif);
            redirectAttributes.addFlashAttribute("ok", "Bon de commande rejeté : il revient en brouillon chez son créateur.");
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String valeur(List<String> valeurs, int i) {
        return valeurs != null && i < valeurs.size() ? valeurs.get(i) : null;
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Utilisateur utilisateurCourant() {
        Utilisateur principal = (Utilisateur) auth().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }
}
