package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.stock.inventaire.Inventaire;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.FamilleService;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import afg.achat.afgApprovAchat.service.stock.InventaireService;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.util.*;

/**
 * Inventaires du stock : historique, ouverture, comptage, soumission et validation
 * (maker / checker Moyens Généraux, voir {@link InventaireService}).
 */
@Slf4j
@Controller
@RequestMapping("/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
public class InventaireController {

    private static final String PREFIXE_QUANTITE = "q_";
    private static final String PREFIXE_COMMENTAIRE = "c_";

    private final InventaireService inventaireService;
    private final FamilleService familleService;
    private final UtilisateurService utilisateurService;

    // ── Historique ───────────────────────────────────────────────────────────

    @GetMapping("/inventaires")
    public String liste(Model model, HttpServletRequest request,
                        @RequestParam(required = false) String statut,
                        @RequestParam(required = false) Integer famille,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                        @RequestParam(required = false) String reference,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        Authentication auth = auth();
        Inventaire.Statut filtreStatut = null;
        if (statut != null && !statut.isBlank()) {
            try {
                filtreStatut = Inventaire.Statut.valueOf(statut.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // statut inconnu : pas de filtre
            }
        }
        int taille = Math.min(Math.max(size, 5), 100);
        Page<Inventaire> inventaires = inventaireService.rechercher(filtreStatut, famille, du, au, reference,
                PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("inventaires", inventaires);
        model.addAttribute("syntheses", inventaireService.syntheses(
                inventaires.getContent().stream().map(Inventaire::getId).toList()));
        model.addAttribute("ouverts", inventaireService.ouverts());
        model.addAttribute("statuts", Inventaire.Statut.values());
        model.addAttribute("familles", familleService.getFamillesTriees());
        model.addAttribute("peutSaisir", InventaireService.peutSaisir(auth));
        model.addAttribute("peutValider", InventaireService.peutValider(auth));
        model.addAttribute("roleChecker", Habilitations.libelle(Habilitations.roleChecker(Habilitations.MG)));

        model.addAttribute("statut", filtreStatut == null ? "" : filtreStatut.name());
        model.addAttribute("famille", famille);
        model.addAttribute("du", du);
        model.addAttribute("au", au);
        model.addAttribute("reference", reference == null ? "" : reference);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        return "stock/inventaire-liste";
    }

    // ── Ouverture ────────────────────────────────────────────────────────────

    /** @param famille identifiant de famille ; vide : inventaire complet */
    @PostMapping("/inventaire/ouvrir")
    public String ouvrir(@RequestParam(required = false) String famille,
                         @RequestParam(required = false) String commentaire,
                         RedirectAttributes ra) {
        if (!InventaireService.peutSaisir(auth())) return refus(ra, "/stock/inventaires");
        try {
            Integer familleId = famille == null || famille.isBlank() ? null : Integer.valueOf(famille.trim());
            Inventaire inv = inventaireService.ouvrir(familleId, commentaire, connecte());
            ra.addFlashAttribute("ok", "Inventaire <strong>" + inv.getReference() + "</strong> ouvert : "
                    + inv.getLignes().size() + " article(s) à compter.");
            return "redirect:/stock/inventaire/" + inv.getId();
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
            return "redirect:/stock/inventaires";
        }
    }

    // ── Fiche (comptage, validation, consultation) ───────────────────────────

    @GetMapping("/inventaire/{id}")
    public String fiche(@PathVariable int id, Model model, RedirectAttributes ra) {
        Inventaire inv;
        try {
            inv = inventaireService.getInventaire(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
            return "redirect:/stock/inventaires";
        }
        Authentication auth = auth();
        Utilisateur moi = connecte();
        model.addAttribute("inv", inv);
        model.addAttribute("synthese", inventaireService.synthese(inv));
        model.addAttribute("peutCompter", inventaireService.peutCompter(inv, auth));
        model.addAttribute("peutDecider", inventaireService.peutDecider(inv, moi, auth));
        model.addAttribute("peutAnnuler", inventaireService.peutAnnuler(inv, moi, auth));
        model.addAttribute("estValideur", InventaireService.peutValider(auth));
        model.addAttribute("roleChecker", Habilitations.libelle(Habilitations.roleChecker(Habilitations.MG)));
        // Stock du jour : utile tant que l'écart n'est pas passé en stock
        model.addAttribute("stocksActuels", inv.estOuvert() ? inventaireService.stocksActuels(inv) : Map.of());
        model.addAttribute("ecartsSansJustification", inv.getLignes().stream()
                .filter(l -> l.aEcart() && (l.getCommentaire() == null || l.getCommentaire().isBlank())).count());
        return "stock/inventaire-fiche";
    }

    /** Feuille de comptage imprimable, sans le stock théorique (comptage à l'aveugle). */
    @GetMapping("/inventaire/{id}/feuille")
    public String feuille(@PathVariable int id, Model model) {
        model.addAttribute("inv", inventaireService.getInventaire(id));
        return "stock/inventaire-feuille";
    }

    /**
     * Enregistre le comptage ; {@code action=soumettre} le soumet ensuite à la validation.
     * Les champs sont {@code q_<ligne>} (quantité) et {@code c_<ligne>} (justification).
     */
    @PostMapping("/inventaire/{id}/comptage")
    public String comptage(@PathVariable int id,
                           @RequestParam Map<String, String> params,
                           @RequestParam(defaultValue = "enregistrer") String action,
                           RedirectAttributes ra) {
        String retour = "redirect:/stock/inventaire/" + id;
        if (!InventaireService.peutSaisir(auth())) return refus(ra, "/stock/inventaire/" + id);
        Map<Integer, String> quantites = new HashMap<>();
        Map<Integer, String> commentaires = new HashMap<>();
        params.forEach((k, v) -> {
            Integer ligne = null;
            if (k.startsWith(PREFIXE_QUANTITE)) {
                ligne = idLigne(k.substring(PREFIXE_QUANTITE.length()));
                if (ligne != null) quantites.put(ligne, v);
            } else if (k.startsWith(PREFIXE_COMMENTAIRE)) {
                ligne = idLigne(k.substring(PREFIXE_COMMENTAIRE.length()));
                if (ligne != null) commentaires.put(ligne, v);
            }
        });

        Utilisateur moi = connecte();
        int modifiees;
        try {
            modifiees = inventaireService.enregistrerComptage(id, quantites, commentaires, moi);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
            return retour;
        }
        if (!"soumettre".equals(action)) {
            ra.addFlashAttribute("ok", modifiees == 0 ? "Aucune modification." : modifiees + " ligne(s) enregistrée(s).");
            return retour;
        }
        try {
            Inventaire inv = inventaireService.soumettre(id, moi);
            ra.addFlashAttribute("ok", "Inventaire <strong>" + inv.getReference()
                    + "</strong> soumis : il attend la validation d'une autre personne. Le stock n'est pas encore ajusté.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", "Comptage enregistré, mais non soumis : " + HtmlUtils.htmlEscape(e.getMessage()));
        }
        return retour;
    }

    // ── Décision ─────────────────────────────────────────────────────────────

    @PostMapping("/inventaire/{id}/valider")
    public String valider(@PathVariable int id, RedirectAttributes ra) {
        try {
            Inventaire inv = inventaireService.valider(id, connecte(), auth());
            ra.addFlashAttribute("ok", "Inventaire <strong>" + inv.getReference()
                    + "</strong> validé : les écarts sont passés en stock.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
        }
        return "redirect:/stock/inventaire/" + id;
    }

    @PostMapping("/inventaire/{id}/rejeter")
    public String rejeter(@PathVariable int id, @RequestParam(required = false) String motif, RedirectAttributes ra) {
        try {
            Inventaire inv = inventaireService.rejeter(id, connecte(), auth(), motif);
            ra.addFlashAttribute("ok", "Inventaire <strong>" + inv.getReference() + "</strong> renvoyé en comptage.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
        }
        return "redirect:/stock/inventaire/" + id;
    }

    @PostMapping("/inventaire/{id}/annuler")
    public String annuler(@PathVariable int id, @RequestParam(required = false) String motif, RedirectAttributes ra) {
        try {
            Inventaire inv = inventaireService.annuler(id, connecte(), auth(), motif);
            ra.addFlashAttribute("ok", "Inventaire <strong>" + inv.getReference() + "</strong> annulé : le stock n'a pas été modifié.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", HtmlUtils.htmlEscape(e.getMessage()));
        }
        return "redirect:/stock/inventaire/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Integer idLigne(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String refus(RedirectAttributes ra, String url) {
        ra.addFlashAttribute("ko", "Action réservée aux Moyens Généraux.");
        return "redirect:" + url;
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Utilisateur connecte() {
        Utilisateur principal = (Utilisateur) auth().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }
}
