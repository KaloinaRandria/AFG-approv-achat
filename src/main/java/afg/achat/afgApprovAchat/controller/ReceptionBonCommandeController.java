package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.Article;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeFille;
import afg.achat.afgApprovAchat.model.bonCommande.BonCommandeMere;
import afg.achat.afgApprovAchat.model.bonLivraison.BonLivraisonMere;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.bonCommande.BonCommandeService;
import afg.achat.afgApprovAchat.service.bonlivraison.GroupeReception;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService.LigneSaisie;
import afg.achat.afgApprovAchat.service.bonlivraison.ReceptionBonCommandeService.SaisieReception;
import afg.achat.afgApprovAchat.service.util.DeviseService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import afg.achat.afgApprovAchat.service.workflow.WorkflowException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Réception d'un bon de commande validé : saisie du bon de livraison pré-rempli depuis le bon
 * de commande (entrée en stock) et clôture d'un reliquat que le fournisseur ne livrera pas.
 */
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@Controller
@RequestMapping("/bonlivraison/reception")
@RequiredArgsConstructor
public class ReceptionBonCommandeController {

    /** Clé de session du jeton anti double-soumission, propre à chaque bon de commande. */
    private static final String JETON = "jetonReception_";

    private final BonCommandeService bonCommandeService;
    private final ReceptionBonCommandeService receptionService;
    private final DeviseService deviseService;
    private final ArticleService articleService;
    private final UtilisateurService utilisateurService;

    @GetMapping("/{idBc}")
    public String formulaire(@PathVariable String idBc, Model model, HttpSession session,
                             RedirectAttributes redirectAttributes) {
        BonCommandeMere bc = bonCommandeService.getBonCommandeMereById(idBc).orElse(null);
        if (bc == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de commande introuvable.");
            return "redirect:/bon-commande/list";
        }
        if (!bc.estReceptionnable()) {
            redirectAttributes.addFlashAttribute("ko", "Ce bon de commande ne peut pas être réceptionné : il doit être"
                    + " validé et sa réception ne doit être ni complète ni soldée.");
            return "redirect:/bon-commande/" + idBc;
        }
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("dateReception", LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString());
        Arrays.stream(deviseService.getAllDevises())
                .filter(d -> "MGA".equalsIgnoreCase(d.getAcronyme()))
                .findFirst()
                .ifPresent(d -> valeurs.put("devise", String.valueOf(d.getId())));
        return afficher(bc, valeurs, model, session);
    }

    @PostMapping("/{idBc}")
    public String receptionner(@PathVariable String idBc,
                               @RequestParam Map<String, String> params,
                               Model model, HttpSession session,
                               RedirectAttributes redirectAttributes) {
        // Jeton consommé dès la première soumission : un double clic ne crée pas deux BL
        String jeton = (String) session.getAttribute(JETON + idBc);
        session.removeAttribute(JETON + idBc);
        if (jeton == null || !jeton.equals(params.get("jeton"))) {
            redirectAttributes.addFlashAttribute("ko",
                    "Cette réception a déjà été soumise. Vérifiez les bons de livraison du bon de commande.");
            return "redirect:/bon-commande/" + idBc;
        }

        BonCommandeMere bc = bonCommandeService.getBonCommandeMereById(idBc).orElse(null);
        if (bc == null) {
            redirectAttributes.addFlashAttribute("ko", "Bon de commande introuvable.");
            return "redirect:/bon-commande/list";
        }

        // Champs nommés d'après l'identifiant de chaque ligne livrée (GroupeReception.getId())
        Map<Integer, LigneSaisie> lignes = new HashMap<>();
        for (GroupeReception g : GroupeReception.grouper(bonCommandeService.getBonCommandeFillesByBonCommandeMere(bc))) {
            lignes.put(g.getId(), new LigneSaisie(params.get("quantite_" + g.getId()),
                    params.get("prix_" + g.getId()), params.get("article_" + g.getId())));
        }
        try {
            BonLivraisonMere bl = receptionService.receptionner(idBc, new SaisieReception(
                    params.get("devise"), params.get("dateReception"), params.get("description"), lignes));
            redirectAttributes.addFlashAttribute("ok", "Réception enregistrée : bon de livraison " + bl.getId()
                    + ". Les quantités reçues sont entrées en stock.");
            return "redirect:/bon-commande/" + idBc;
        } catch (WorkflowException e) {
            // On réaffiche la saisie pour ne rien perdre
            model.addAttribute("ko", e.getMessage());
            return afficher(bc, params, model, session);
        }
    }

    @PostMapping("/{idBc}/solder")
    public String solder(@PathVariable String idBc, @RequestParam(required = false) String motif,
                         RedirectAttributes redirectAttributes) {
        try {
            receptionService.solderReliquat(idBc, motif, utilisateurCourant());
            redirectAttributes.addFlashAttribute("ok", "Réception soldée : le reliquat ne sera pas livré.");
        } catch (WorkflowException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/bon-commande/" + idBc;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String afficher(BonCommandeMere bc, Map<String, String> valeurs, Model model, HttpSession session) {
        String jeton = UUID.randomUUID().toString();
        session.setAttribute(JETON + bc.getId(), jeton);

        List<BonCommandeFille> lignes = bonCommandeService.getBonCommandeFillesByBonCommandeMere(bc);
        Devise[] devises = deviseService.getAllDevises();
        List<Article> articlesActifs = Arrays.stream(articleService.getAllArticles())
                .filter(Article::estActif)
                .toList();

        model.addAttribute("jeton", jeton);
        model.addAttribute("bonCommandeMere", bc);
        // Une ligne par ligne du bon imprimé : les lignes de demandes fusionnées se reçoivent ensemble
        model.addAttribute("lignes", GroupeReception.grouper(lignes));
        model.addAttribute("devises", devises);
        model.addAttribute("articlesActifs", articlesActifs);
        model.addAttribute("valeurs", valeurs);
        model.addAttribute("bonsLivraison", receptionService.getBonsLivraison(bc));
        return "bl/bl-reception";
    }

    private Utilisateur utilisateurCourant() {
        Utilisateur principal = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }
}
