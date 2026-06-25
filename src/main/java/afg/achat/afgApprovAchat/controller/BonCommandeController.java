package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.Service;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.FournisseurService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
                                           String dateCreation,
                                           String dateLivraisonPrevue,
                                           String lieuLivraison,
                                           String fournisseurId,
                                           @RequestParam(name = "submissionToken") String submissionToken,
                                           Model model,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes) {
        String sessionToken = (String) session.getAttribute("submissionToken");
        if (sessionToken == null || !sessionToken.equals(submissionToken)) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "La soumission du formulaire a déjà été effectuée. Veuillez ne pas soumettre à nouveau.");
            return "redirect:/bon-commande/list";
        }
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
        bonCommandeMere.setDateCreation(LocalDateTime.parse(dateCreation));
        bonCommandeMere.setDateLivraisonPrevue(LocalDateTime.parse(dateLivraisonPrevue));
        bonCommandeMere.setLieuLivraison(lieuLivraison);

        //Bon de commande Draft par defaut
        bonCommandeMere.setStatut(BonCommandeMere.StatutBonCommande.BROUILLON);

        //Description du bon de commande = motif evoque de la demande mere
        bonCommandeMere.setDescription(demandeMere.getMotifEvoque());

        //recuperation du fournisseur selectionne
        Fournisseur fournisseur = fournisseurService.getById(Integer.parseInt(fournisseurId));
        bonCommandeMere.setFournisseur(fournisseur);

        //Createur du Bon de commande
        bonCommandeMere.setCreateur(utilisateur);



        bonCommandeMere.setDemandeMere(demandeMere);

        model.addAttribute("demandeMereId", demandeMereId);
        return "bc/bon-commande-saisie";
    }
}
