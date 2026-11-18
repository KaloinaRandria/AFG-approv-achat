package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.FournisseurAutocompleteDTO;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.fournisseur.AdresseFournisseur;
import afg.achat.afgApprovAchat.model.fournisseur.ResponsableFournisseur;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.bonCommande.BesoinAchatService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeDemandeService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeManuelService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.bonCommande.LigneImprimee;
import afg.achat.afgApprovAchat.service.bonCommande.SaisieBonCommandeDemande;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService;
import afg.achat.afgApprovAchat.service.fournisseur.AdresseFournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import afg.achat.afgApprovAchat.service.fournisseur.ResponsableFournisseurService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import afg.achat.afgApprovAchat.service.workflow.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bons de commande : liste, fiche et bon issu d'une demande (complété par les Moyens Généraux).
 * Le bon saisi sans demande est géré par {@link BonCommandeManuelController} ; les deux
 * suivent le même circuit de validation (Moyens Généraux puis S.G.).
 */
// Consultation : Moyens Généraux et acteurs du bon de commande (créateur, validateur MG, S.G.).
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX','MG_CREATOR','MG_VALIDATOR','SG')")
@Controller
@RequestMapping("/bon-commande")
@RequiredArgsConstructor
public class BonCommandeController {

    /** Préfixe des champs de désignation fournisseur : designation_&lt;id de la ligne&gt;. */
    private static final String PREFIXE_DESIGNATION = "designation_";

    private final BonCommandeService bonCommandeService;
    private final BonCommandeDemandeService bonCommandeDemandeService;
    private final BonCommandeManuelService bonCommandeManuelService;
    private final BesoinAchatService besoinAchatService;
    private final FournisseurService fournisseurService;
    private final UtilisateurService utilisateurService;
    private final ResponsableFournisseurService responsableFournisseurService;
    private final AdresseFournisseurService adresseFournisseurService;
    private final ReceptionBonCommandeService receptionBonCommandeService;

    // ── Liste ────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String bonCommandeList(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String statut,
                                  @RequestParam(defaultValue = "false") boolean aValider,
                                  @RequestParam(required = false) String numero,
                                  @RequestParam(required = false) String fournisseur,
                                  @RequestParam(required = false) String objet,
                                  @RequestParam(required = false) String demande,
                                  @RequestParam(required = false) String article,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  HttpServletRequest request,
                                  Model model) {
        Authentication auth = auth();

        BonCommandeMere.StatutBonCommande statutFiltre = null;
        if (statut != null && !statut.isBlank()) {
            try {
                statutFiltre = BonCommandeMere.StatutBonCommande.valueOf(statut.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // statut inconnu : pas de filtre
            }
        }

        // Étapes que l'utilisateur connecté peut valider (filtre « À valider par moi »)
        List<BonCommandeMere.EtapeValidation> etapesValidables = Arrays.stream(BonCommandeMere.EtapeValidation.values())
                .filter(e -> WorkflowService.hasRole(auth, "ROLE_" + e.getRole()) || WorkflowService.hasRole(auth, "ROLE_ADMIN"))
                .toList();

        int taille = Math.min(Math.max(size, 5), 100);
        Page<BonCommandeMere> bons = bonCommandeService.rechercher(new BonCommandeService.Recherche(
                        statutFiltre, aValider, etapesValidables, dateFrom, dateTo,
                        numero, fournisseur, objet, demande, article),
                PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("bons", bons);
        // Bons de la page qui attendent une décision de l'utilisateur connecté
        model.addAttribute("idsAValider", bons.getContent().stream()
                .filter(bc -> BonCommandeManuelService.peutValider(bc, auth))
                .map(BonCommandeMere::getId)
                .toList());
        model.addAttribute("statuts", BonCommandeMere.StatutBonCommande.values());
        model.addAttribute("statut", statutFiltre == null ? "" : statutFiltre.name());
        model.addAttribute("aValider", aValider);
        model.addAttribute("peutValiderUneEtape", !etapesValidables.isEmpty());
        model.addAttribute("numero", numero == null ? "" : numero);
        model.addAttribute("fournisseur", fournisseur == null ? "" : fournisseur);
        model.addAttribute("objet", objet == null ? "" : objet);
        model.addAttribute("demande", demande == null ? "" : demande);
        model.addAttribute("article", article == null ? "" : article);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        model.addAttribute("peutCreer", BonCommandeManuelService.peutCreer(auth));
        return "bc/bon-commande-liste";
    }

    // ── Lignes à acheter ─────────────────────────────────────────────────────

    /**
     * Lignes de demandes validées encore à commander, toutes demandes confondues.
     * Les Moyens Généraux en cochent plusieurs pour créer un bon de commande groupé.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX','MG_CREATOR')")
    @GetMapping("/lignes-a-acheter")
    public String lignesAAcheter(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size,
                                 @RequestParam(required = false) String demande,
                                 @RequestParam(required = false) String demandeur,
                                 @RequestParam(required = false) String article,
                                 @RequestParam(required = false) String motif,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                 HttpServletRequest request,
                                 Model model) {
        int taille = Math.min(Math.max(size, 5), 100);
        Page<BesoinAchatService.BesoinAchat> besoins = besoinAchatService.lignesEnAttente(
                new BesoinAchatService.Recherche(demande, demandeur, article, motif, dateFrom, dateTo),
                PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("besoins", besoins);
        model.addAttribute("demande", demande == null ? "" : demande);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("article", article == null ? "" : article);
        model.addAttribute("motif", motif == null ? "" : motif);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        return "bc/lignes-a-acheter";
    }

    // ── Bon issu d'une demande (bons existants) ──────────────────────────────
    // La création depuis la fiche de la demande est retirée : un bon de commande regroupe
    // désormais les lignes de plusieurs demandes (écran « Lignes à acheter »). Les brouillons
    // déjà créés depuis une demande peuvent encore être complétés, soumis ou annulés.

    @PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
    @PostMapping("/sauvegarder")
    public String sauvegarderBonCommande(
            @RequestParam(name = "idBcMere") String idBcMere,
            @RequestParam(required = false) String numero,
            @RequestParam(required = false) String dateLivraisonPrevue,
            @RequestParam(name = "fournisseurs", required = false) String fournisseurId,
            @RequestParam(required = false) String actionResponsable, // "update", "new" ou vide
            @RequestParam(required = false) String responsableFournisseurId,
            @RequestParam(required = false) String nouveauResponsableNom,
            @RequestParam(required = false) String nouveauResponsablePrenom,
            @RequestParam(required = false) String nouveauResponsableContact,
            @RequestParam(required = false) String referenceFournisseur,
            @RequestParam(required = false) String lieuLivraison,
            @RequestParam(required = false) Double tauxTVA,
            @RequestParam(required = false) Double tauxRemise,
            @RequestParam(required = false) String actionAdresse, // "update", "new" ou vide
            @RequestParam(required = false) String adresseFournisseurId,
            @RequestParam(required = false) String nouvelleAdresseLibelle,
            @RequestParam(required = false) String action, // "enregistrer" ou "soumettre"
            @RequestParam Map<String, String> parametres,
            RedirectAttributes redirectAttributes) {
        // Désignations fournisseur envoyées sous la forme designation_<id de la ligne>
        Map<Integer, String> designations = new HashMap<>();
        parametres.forEach((cle, valeur) -> {
            if (!cle.startsWith(PREFIXE_DESIGNATION)) return;
            try {
                designations.put(Integer.parseInt(cle.substring(PREFIXE_DESIGNATION.length())), valeur);
            } catch (NumberFormatException ignored) {
                // champ inattendu : ignoré
            }
        });

        SaisieBonCommandeDemande saisie = new SaisieBonCommandeDemande(idBcMere, numero, dateLivraisonPrevue,
                fournisseurId, referenceFournisseur, lieuLivraison, tauxRemise, tauxTVA,
                new SaisieBonCommandeDemande.Responsable(actionResponsable, responsableFournisseurId,
                        nouveauResponsableNom, nouveauResponsablePrenom, nouveauResponsableContact),
                new SaisieBonCommandeDemande.Adresse(actionAdresse, adresseFournisseurId, nouvelleAdresseLibelle),
                designations);
        try {
            BonCommandeMere bc = bonCommandeDemandeService.enregistrer(saisie, auth());
            if ("soumettre".equals(action)) {
                // La saisie reste enregistrée si la soumission est refusée (ex. fournisseur manquant)
                bonCommandeManuelService.soumettre(bc.getId(), utilisateurCourant(), auth());
                redirectAttributes.addFlashAttribute("ok", "Bon de commande n° " + bc.getNumero()
                        + " enregistré et soumis à la validation des Moyens Généraux.");
            } else {
                redirectAttributes.addFlashAttribute("ok", "Bon de commande n° " + bc.getNumero() + " enregistré.");
            }
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + idBcMere;
    }

    // ── Fiche ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String afficherBonCommande(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        BonCommandeMere bonCommandeMere = bonCommandeService.getBonCommandeMereById(id).orElse(null);
        if (bonCommandeMere == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de commande introuvable.");
            return "redirect:/bon-commande/list";
        }
        List<BonCommandeFille> lignes = bonCommandeService.getBonCommandeFillesByBonCommandeMere(bonCommandeMere);
        Authentication auth = auth();

        // Brouillon issu d'une demande : écran de saisie des Moyens Généraux
        if (BonCommandeDemandeService.peutCompleter(bonCommandeMere, auth)) {
            model.addAttribute("bonCommandeMere", bonCommandeMere);
            model.addAttribute("bonCommandeFilles", lignes);
            model.addAttribute("peutSoumettre", BonCommandeManuelService.peutSoumettre(bonCommandeMere, auth));
            model.addAttribute("fournisseurs", fournisseursPourSaisie());

            if (bonCommandeMere.getFournisseur() != null) {
                int fournisseurId = bonCommandeMere.getFournisseur().getId();
                responsableFournisseurService.getResponsableActuel(fournisseurId)
                        .ifPresent(r -> model.addAttribute("responsableFournisseurActuel", r));
                adresseFournisseurService.getAdresseActuel(fournisseurId)
                        .ifPresent(a -> model.addAttribute("adresseFournisseurActuel", a));
            }
            return "bc/bon-commande-saisie";
        }

        model.addAttribute("bonCommandeMere", bonCommandeMere);
        model.addAttribute("lignes", lignes);
        // Présentation fournisseur : lignes identiques (article, désignation, unité, prix) fusionnées
        model.addAttribute("lignesImprimees", LigneImprimee.regrouper(lignes));
        model.addAttribute("peutModifier", BonCommandeManuelService.peutModifier(bonCommandeMere, auth));
        model.addAttribute("peutSoumettre", BonCommandeManuelService.peutSoumettre(bonCommandeMere, auth));
        model.addAttribute("peutValider", BonCommandeManuelService.peutValider(bonCommandeMere, auth));

        // Réception : bons de livraison rattachés ; saisie réservée aux Moyens Généraux
        boolean gereLaReception = WorkflowService.hasRole(auth, "ROLE_MOYENS_GENERAUX")
                || WorkflowService.hasRole(auth, "ROLE_ADMIN");
        model.addAttribute("bonsLivraison", receptionBonCommandeService.getBonsLivraison(bonCommandeMere));
        model.addAttribute("peutReceptionner", gereLaReception && bonCommandeMere.estReceptionnable());
        model.addAttribute("peutSolder", gereLaReception && ReceptionBonCommandeService.peutSolder(bonCommandeMere));

        // Montants du récapitulatif (la remise et la TVA sont enregistrées en pourcentage)
        double montantHT = lignes.stream().mapToDouble(BonCommandeFille::getMontantHT).sum();
        double montantRemise = montantHT * bonCommandeMere.getRemise() / 100;
        double montantHTRemise = montantHT - montantRemise;
        double montantTVA = montantHTRemise * bonCommandeMere.getTauxTVA() / 100;
        model.addAttribute("montantHT", montantHT);
        model.addAttribute("montantRemise", montantRemise);
        model.addAttribute("montantHTRemise", montantHTRemise);
        model.addAttribute("montantTVA", montantTVA);
        model.addAttribute("montantTTC", montantHTRemise + montantTVA);
        return "bc/bon-commande-fiche";
    }

    /** Fournisseurs pour l'autocomplétion, avec leur responsable et leur adresse actuels. */
    private List<FournisseurAutocompleteDTO> fournisseursPourSaisie() {
        return Arrays.stream(fournisseurService.getAllFournisseurs())
                .map(f -> {
                    var respOpt = responsableFournisseurService.getResponsableActuel(f.getId());
                    var adrOpt = adresseFournisseurService.getAdresseActuel(f.getId());
                    return new FournisseurAutocompleteDTO(
                            f.getId(), f.getNom(),
                            respOpt.map(ResponsableFournisseur::getId).orElse(null),
                            respOpt.map(rf -> rf.getResponsable().getId()).orElse(null),
                            respOpt.map(rf -> rf.getResponsable().getPrenom() + " " + rf.getResponsable().getNom()).orElse(""),
                            adrOpt.map(AdresseFournisseur::getId).orElse(null),
                            adrOpt.map(af -> af.getAdresse().getId()).orElse(null),
                            adrOpt.map(af -> af.getAdresse().getLibelle()).orElse(""));
                })
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Utilisateur utilisateurCourant() {
        Utilisateur principal = (Utilisateur) auth().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }
}
