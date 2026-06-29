package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.FournisseurService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@PreAuthorize("hasAnyRole('ADMIN', 'MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bon-commande")
@RequiredArgsConstructor
public class BonCommandeController {
    private final BonCommandeService bonCommandeService;
    private final DemandeMereService demandeMereService;
    private final DemandeFilleService demandeFilleService;
    private final FournisseurService fournisseurService;
    private final UtilisateurService utilisateurService;
    private final IdGenerator idGenerator;

    @GetMapping("")
    public String goToBonCommandePage() {
        //redirection vers la fiche du bon de commande dans une demande validee
        return "";
    }

    @GetMapping("/list")
    public String bonCommandeList() {
        return "";
    }
    @PostMapping("/creer/{id}")
    public String creeBonCommandeByDemande(@PathVariable(name = "id") String demandeMereId,
                                           String numero,
                                           String referenceFournisseur,
                                           String lieuLivraison,
                                           String fournisseurId,
                                           Model model) {
        //Creation d'un Bon de commande a partir d'une demande d'achat

        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());
        //Demande Mere
        DemandeMere demandeMere = demandeMereService.getDemandeById(demandeMereId);
        //Demande Fille
        List<DemandeFille> demandeFilles = demandeFilleService.getDemandeFilleByDemandeMere(demandeMere);

        //Creation du bon de commande
        BonCommandeMere bonCommandeMere = new BonCommandeMere();
        bonCommandeMere.setNumero(numero);
        bonCommandeMere.setReferenceFournisseur(referenceFournisseur);
        bonCommandeMere.setDateCreation(LocalDateTime.now());
        bonCommandeMere.setLieuLivraison(lieuLivraison);

        //Bon de commande Draft par defaut
        bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);

        //Description du bon de commande = motif evoque de la demande mere
        bonCommandeMere.setDescription(demandeMere.getMotifEvoque());

        //recuperation du fournisseur selectionne si fourni
        if (fournisseurId != null && !fournisseurId.isEmpty()) {
            Fournisseur fournisseur = fournisseurService.getById(Integer.parseInt(fournisseurId));
            bonCommandeMere.setFournisseur(fournisseur);
        }

        //Createur du Bon de commande
        bonCommandeMere.setCreateur(utilisateur);

        bonCommandeMere.setDemandeMere(demandeMere);

        // Créer les lignes du bon de commande à partir des demandes filles
        List<BonCommandeFille> bonCommandeFilles = new ArrayList<>();
        for (DemandeFille demandeFille : demandeFilles) {
            BonCommandeFille bcFille = new BonCommandeFille();
            bcFille.setBonCommandeMere(bonCommandeMere);
            bcFille.setDemandeFille(demandeFille);
            bcFille.setArticle(demandeFille.getArticle());
            bcFille.setQuantiteCommandee(demandeFille.getQuantite());
            bcFille.setQuantiteRestante(demandeFille.getQuantite());

            // Initialiser les prix et montants si disponibles
            if (demandeFille.getPrixUnitaire() != null) {
                bcFille.setPrixUnitaireHT(demandeFille.getPrixUnitaire());
                bcFille.setMontantHT(demandeFille.getQuantite() * demandeFille.getPrixUnitaire());
            }
            if (demandeFille.getMontantEstime() != null) {
                bcFille.setMontantTTC(demandeFille.getMontantEstime());
            }

            bonCommandeFilles.add(bcFille);
        }

        // Ajouter les objets nécessaires au template pour affichage et saisie des lignes
        model.addAttribute("demandeMereId", demandeMereId);
        model.addAttribute("bonCommandeMere", bonCommandeMere);
        model.addAttribute("bonCommandeFilles", bonCommandeFilles);
        model.addAttribute("fournisseurs", fournisseurService.getAllFournisseurs());

        return "bc/bon-commande-saisie";
    }

    @PostMapping("/sauvegarder")
    public String sauvegarderBonCommande(
            @RequestParam(required = false) String idBcMere,
            @RequestParam String demandeMereId,
            @RequestParam(required = false) String dateLivraisonPrevue,
            @RequestParam(name = "fournisseurs", required = false) String fournisseurId,
            @RequestParam(required = false) String referenceFournisseur,
            @RequestParam(required = false) String lieuLivraison,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            // Récupérer ou créer le BC
            BonCommandeMere bonCommandeMere;
            if (idBcMere == null || idBcMere.isEmpty()) {
                bonCommandeMere = new BonCommandeMere();
                bonCommandeMere.setId(idGenerator);
            } else {
                bonCommandeMere = bonCommandeService.getBonCommandeMereById(idBcMere)
                        .orElse(new BonCommandeMere());
                if (bonCommandeMere.getId() == null) {
                    bonCommandeMere.setId(idGenerator);
                }
            }

            // Mettre à jour les champs du BC
            if (dateLivraisonPrevue != null && !dateLivraisonPrevue.isEmpty()) {
                bonCommandeMere.setDateLivraisonPrevue(LocalDateTime.parse(dateLivraisonPrevue));
            }
            if (fournisseurId != null && !fournisseurId.isEmpty()) {
                Fournisseur fournisseur = fournisseurService.getById(Integer.parseInt(fournisseurId));
                bonCommandeMere.setFournisseur(fournisseur);
            }
            if (referenceFournisseur != null && !referenceFournisseur.isEmpty()) {
                bonCommandeMere.setReferenceFournisseur(referenceFournisseur);
            }
            if (lieuLivraison != null && !lieuLivraison.isEmpty()) {
                bonCommandeMere.setLieuLivraison(lieuLivraison);
            }

            bonCommandeMere.setDemandeMere(demandeMereService.getDemandeById(demandeMereId));

            // Sauvegarder le BC mère
            BonCommandeMere bcSauvegardee = bonCommandeService.saveBonCommandeMere(bonCommandeMere);

            // Récupérer les lignes du formulaire et créer les BonCommandeFille
            String[] demandeFilleIds = request.getParameterValues("lignes[0].demandeFilleId");
            if (demandeFilleIds != null && demandeFilleIds.length > 0) {
                int i = 0;
                while (request.getParameter("lignes[" + i + "].demandeFilleId") != null) {
                    String[] params = request.getParameterValues("lignes[" + i + "].demandeFilleId");
                    if (params != null && params.length > 0) {
                        BonCommandeFille bcFille = new BonCommandeFille();
                        bcFille.setBonCommandeMere(bcSauvegardee);

                        // Récupérer la demande fille
                        int demandeFilleId = Integer.parseInt(request.getParameter("lignes[" + i + "].demandeFilleId"));
                        DemandeFille demandeFille = demandeFilleService.getDemandeFilleById(demandeFilleId);
                        bcFille.setDemandeFille(demandeFille);

                        // Récupérer l'article
                        String articleIdParam = request.getParameter("lignes[" + i + "].articleId");
                        if (articleIdParam != null && !articleIdParam.isEmpty()) {
                            // À récupérer depuis un service d'article si disponible
                            bcFille.setArticle(demandeFille.getArticle());
                        }

                        // Définir les données de la ligne
                        String designationFournisseur = request.getParameter("lignes[" + i + "].designationFournisseur");
                        if (designationFournisseur != null) {
                            bcFille.setDesignationFournisseur(designationFournisseur);
                        }

                        String quantiteParam = request.getParameter("lignes[" + i + "].quantiteCommandee");
                        if (quantiteParam != null && !quantiteParam.isEmpty()) {
                            double quantite = Double.parseDouble(quantiteParam);
                            bcFille.setQuantiteCommandee(quantite);
                            bcFille.setQuantiteRestante(quantite);
                        }

                        String prixParam = request.getParameter("lignes[" + i + "].prixUnitaireHT");
                        if (prixParam != null && !prixParam.isEmpty()) {
                            double prix = Double.parseDouble(prixParam);
                            bcFille.setPrixUnitaireHT(prix);
                        }

                        String montantParam = request.getParameter("lignes[" + i + "].montantHT");
                        if (montantParam != null && !montantParam.isEmpty()) {
                            double montant = Double.parseDouble(montantParam);
                            bcFille.setMontantHT(montant);
                        }

                        // Sauvegarder la ligne
                        bonCommandeService.saveBonCommandeFille(bcFille);
                    }
                    i++;
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Bon de commande n° " + bcSauvegardee.getNumero() + " sauvegardé avec succès avec " +
                    (demandeFilleIds != null ? demandeFilleIds.length : 0) + " ligne(s).");
            return "redirect:/bon-commande/" + bcSauvegardee.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la sauvegarde du bon de commande : " + e.getMessage());
            return "redirect:/bon-commande/list";
        }
    }

    @GetMapping("/{id}")
    public String afficherBonCommande(@PathVariable String id, Model model) {
        BonCommandeMere bonCommandeMere = bonCommandeService.getBonCommandeMereById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bon de commande non trouvé"));
        List<BonCommandeFille> lignes = bonCommandeService.getBonCommandeFillesByBonCommandeMere(bonCommandeMere);

        model.addAttribute("bonCommandeMere", bonCommandeMere);
        model.addAttribute("lignes", lignes);
        return "bc/bon-commande-fiche";
    }
}
