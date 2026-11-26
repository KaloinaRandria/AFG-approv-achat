package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.perdiem.BaremePerDiem;
import afg.achat.afgApprovAchat.model.perdiem.CategoriePersonnel;
import afg.achat.afgApprovAchat.model.perdiem.TypeIndemnite;
import afg.achat.afgApprovAchat.model.perdiem.ZoneMission;
import afg.achat.afgApprovAchat.model.util.Devise;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.util.DeviseRepo;
import afg.achat.afgApprovAchat.service.perdiem.BaremePerDiemService;
import afg.achat.afgApprovAchat.service.perdiem.ReferentielMissionService;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Missions et per diem, paramétrage : barème (maker / checker) et référentiels (catégories, zones).
 * Consultation : RH et administrateur ; les droits d'action sont contrôlés dans les services.
 */
@Controller
@RequestMapping("/perdiem")
@PreAuthorize("hasAnyRole('ADMIN','RH')")
@RequiredArgsConstructor
public class PerDiemController {

    private static final String BAREME = "redirect:/perdiem/bareme";
    private static final String REFERENTIELS = "redirect:/perdiem/referentiels";

    private final BaremePerDiemService baremeService;
    private final ReferentielMissionService referentielService;
    private final UtilisateurService utilisateurService;
    private final DeviseRepo deviseRepo;

    // ── Barème ───────────────────────────────────────────────────────────────

    @GetMapping("/bareme")
    public String bareme(@RequestParam(name = "date", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         Model model) {
        Authentication auth = auth();
        LocalDate jour = date != null ? date : LocalDate.now();
        model.addAttribute("dateGrille", jour);
        model.addAttribute("aujourdhui", LocalDate.now());
        model.addAttribute("types", TypeIndemnite.values());
        model.addAttribute("categories", referentielService.categoriesActives());
        model.addAttribute("zones", referentielService.zonesActives());
        model.addAttribute("grille", baremeService.grille(jour));
        model.addAttribute("grilleEnAttente", baremeService.grilleEnAttente());
        model.addAttribute("enAttente", baremeService.enAttente());
        model.addAttribute("programmes", baremeService.programmesApres(LocalDate.now()));
        model.addAttribute("historique", baremeService.historique());
        model.addAttribute("devises", deviseRepo.findAll());
        model.addAttribute("codeDeviseReference", Devise.CODE_REFERENCE);
        model.addAttribute("peutProposer", BaremePerDiemService.peutProposer(auth));
        model.addAttribute("peutValider", BaremePerDiemService.peutValider(auth));
        model.addAttribute("estAdmin", Habilitations.estAdmin(auth));
        model.addAttribute("moi", connecte());
        return "perdiem/bareme";
    }

    @PostMapping("/bareme/proposer")
    public String proposer(@RequestParam(name = "idCategorie", required = false) Integer idCategorie,
                           @RequestParam(name = "idZone", required = false) Integer idZone,
                           @RequestParam(name = "typeIndemnite", required = false) String type,
                           @RequestParam(name = "montant", required = false) String montant,
                           @RequestParam(name = "idDevise", required = false) Integer idDevise,
                           @RequestParam(name = "dateEffet", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEffet,
                           @RequestParam(name = "motif", required = false) String motif,
                           RedirectAttributes ra) {
        return executer(ra, BAREME, () -> {
            BaremePerDiem b = baremeService.proposer(auth(), connecte(), idCategorie, idZone, type,
                    montant, idDevise, dateEffet, motif);
            return "Taux proposé (" + libelle(b) + ") : en attente de validation par le DRH.";
        });
    }

    @PostMapping("/bareme/{id}/valider")
    public String valider(@PathVariable int id,
                          @RequestParam(name = "commentaire", required = false) String commentaire,
                          RedirectAttributes ra) {
        return executer(ra, BAREME, () -> {
            BaremePerDiem b = baremeService.valider(auth(), connecte(), id, commentaire);
            return "Taux validé (" + libelle(b) + ").";
        });
    }

    @PostMapping("/bareme/{id}/rejeter")
    public String rejeter(@PathVariable int id,
                          @RequestParam(name = "commentaire", required = false) String commentaire,
                          RedirectAttributes ra) {
        return executer(ra, BAREME, () -> {
            BaremePerDiem b = baremeService.rejeter(auth(), connecte(), id, commentaire);
            return "Proposition rejetée (" + libelle(b) + ").";
        });
    }

    @PostMapping("/bareme/{id}/annuler")
    public String annuler(@PathVariable int id, RedirectAttributes ra) {
        return executer(ra, BAREME, () -> {
            BaremePerDiem b = baremeService.annuler(auth(), connecte(), id);
            return "Proposition annulée (" + libelle(b) + ").";
        });
    }

    // ── Référentiels : catégories de personnel et zones ──────────────────────

    @GetMapping("/referentiels")
    public String referentiels(Model model) {
        model.addAttribute("categories", referentielService.categories());
        model.addAttribute("zones", referentielService.zones());
        model.addAttribute("peutGerer", ReferentielMissionService.peutGerer(auth()));
        return "perdiem/referentiels";
    }

    @PostMapping("/categorie/ajouter")
    public String ajouterCategorie(@RequestParam(name = "libelle", required = false) String libelle,
                                   @RequestParam(name = "ordre", required = false) String ordre,
                                   RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            CategoriePersonnel c = referentielService.creerCategorie(auth(), libelle, ordre);
            return "Catégorie « " + c.getLibelle() + " » ajoutée.";
        });
    }

    @PostMapping("/categorie/{id}/modifier")
    public String modifierCategorie(@PathVariable int id,
                                    @RequestParam(name = "libelle", required = false) String libelle,
                                    @RequestParam(name = "ordre", required = false) String ordre,
                                    RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            CategoriePersonnel c = referentielService.modifierCategorie(auth(), id, libelle, ordre);
            return "Catégorie « " + c.getLibelle() + " » modifiée.";
        });
    }

    @PostMapping("/categorie/{id}/activation")
    public String activerCategorie(@PathVariable int id, @RequestParam(name = "actif") boolean actif,
                                   RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            CategoriePersonnel c = referentielService.activerCategorie(auth(), id, actif);
            return "Catégorie « " + c.getLibelle() + " » " + (actif ? "réactivée." : "désactivée.");
        });
    }

    @PostMapping("/zone/ajouter")
    public String ajouterZone(@RequestParam(name = "libelle", required = false) String libelle,
                              @RequestParam(name = "etranger", defaultValue = "false") boolean etranger,
                              @RequestParam(name = "ordre", required = false) String ordre,
                              RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            ZoneMission z = referentielService.creerZone(auth(), libelle, etranger, ordre);
            return "Zone « " + z.getLibelle() + " » ajoutée.";
        });
    }

    @PostMapping("/zone/{id}/modifier")
    public String modifierZone(@PathVariable int id,
                               @RequestParam(name = "libelle", required = false) String libelle,
                               @RequestParam(name = "etranger", defaultValue = "false") boolean etranger,
                               @RequestParam(name = "ordre", required = false) String ordre,
                               RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            ZoneMission z = referentielService.modifierZone(auth(), id, libelle, etranger, ordre);
            return "Zone « " + z.getLibelle() + " » modifiée.";
        });
    }

    @PostMapping("/zone/{id}/activation")
    public String activerZone(@PathVariable int id, @RequestParam(name = "actif") boolean actif,
                              RedirectAttributes ra) {
        return executer(ra, REFERENTIELS, () -> {
            ZoneMission z = referentielService.activerZone(auth(), id, actif);
            return "Zone « " + z.getLibelle() + " » " + (actif ? "réactivée." : "désactivée.");
        });
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    /** Exécute l'action, affiche son message (ok) ou l'erreur (ko), puis revient à la page. */
    private static String executer(RedirectAttributes ra, String page, Supplier<String> action) {
        try {
            ra.addFlashAttribute("ok", action.get());
        } catch (IllegalArgumentException | AccessDeniedException e) {
            ra.addFlashAttribute("ko", e.getMessage());
        }
        return page;
    }

    private static String libelle(BaremePerDiem b) {
        return b.getTypeIndemnite().getLibelle() + " / " + b.getCategorie().getLibelle()
                + " / " + b.getZone().getLibelle();
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Utilisateur connecte() {
        Utilisateur principal = (Utilisateur) auth().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }
}
