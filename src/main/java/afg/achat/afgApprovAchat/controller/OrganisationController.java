package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.util.Service;
import afg.achat.afgApprovAchat.model.utilisateur.Poste;
import afg.achat.afgApprovAchat.service.utilisateur.OrganisationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Supplier;

/** Paramétrage des directions et des postes : Moyens Généraux et administrateur. */
@Controller
@RequestMapping("/organisation")
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@RequiredArgsConstructor
public class OrganisationController {

    private static final String DIRECTIONS = "redirect:/organisation?onglet=directions";
    private static final String POSTES = "redirect:/organisation?onglet=postes";

    private final OrganisationService organisationService;

    @GetMapping
    public String page(@RequestParam(name = "onglet", defaultValue = "directions") String onglet,
                       @RequestParam(name = "direction", required = false) Integer direction,
                       Model model) {
        // Direction présélectionnée dans le filtre des postes (lien « Postes » de l'onglet Directions)
        model.addAttribute("directionFiltre", direction);
        model.addAttribute("onglet", java.util.Set.of("postes", "coherence").contains(onglet) ? onglet : "directions");
        model.addAttribute("directions", organisationService.getDirections());
        model.addAttribute("nbParDirection", organisationService.nombreUtilisateursParDirection());
        model.addAttribute("nbPostesParDirection", organisationService.nombrePostesParDirection());
        model.addAttribute("incoherents", organisationService.utilisateursIncoherents());
        model.addAttribute("postes", organisationService.getPostes());
        model.addAttribute("nbParPoste", organisationService.nombreUtilisateursParPoste());
        return "organisation/organisation";
    }

    // ── Directions ───────────────────────────────────────────────────────────

    @PostMapping("/directions/ajouter")
    public String ajouterDirection(@RequestParam(required = false) String libelle,
                                   @RequestParam(required = false) String acronyme,
                                   RedirectAttributes ra) {
        return executer(ra, DIRECTIONS, () -> {
            Service s = organisationService.creerDirection(libelle, acronyme);
            return "Direction « " + s.getLibelle() + " » (" + s.getAcronyme() + ") ajoutée.";
        });
    }

    @PostMapping("/directions/{id}/modifier")
    public String modifierDirection(@PathVariable int id,
                                    @RequestParam(required = false) String libelle,
                                    @RequestParam(required = false) String acronyme,
                                    RedirectAttributes ra) {
        return executer(ra, DIRECTIONS, () -> {
            Service s = organisationService.modifierDirection(id, libelle, acronyme);
            return "Direction « " + s.getLibelle() + " » (" + s.getAcronyme() + ") modifiée.";
        });
    }

    @PostMapping("/directions/{id}/supprimer")
    public String supprimerDirection(@PathVariable int id, RedirectAttributes ra) {
        return executer(ra, DIRECTIONS, () ->
                "Direction « " + organisationService.supprimerDirection(id).getLibelle() + " » supprimée.");
    }

    // ── Postes ───────────────────────────────────────────────────────────────

    @PostMapping("/postes/ajouter")
    public String ajouterPoste(@RequestParam(required = false) String libelle,
                               @RequestParam(required = false) String direction,
                               RedirectAttributes ra) {
        return executer(ra, POSTES, () ->
                "Poste « " + organisationService.creerPoste(libelle, direction).getPoste() + " » ajouté.");
    }

    @PostMapping("/postes/{id}/modifier")
    public String modifierPoste(@PathVariable int id,
                                @RequestParam(required = false) String libelle,
                                @RequestParam(required = false) String direction,
                                RedirectAttributes ra) {
        return executer(ra, POSTES, () -> {
            Poste p = organisationService.modifierPoste(id, libelle, direction);
            return "Poste « " + p.getPoste() + " » modifié.";
        });
    }

    /** Rattache les postes cochés à une direction, ou les détache si aucune n'est choisie. */
    @PostMapping("/postes/rattacher")
    public String rattacherPostes(@RequestParam(name = "postes", required = false) java.util.List<Integer> postes,
                                  @RequestParam(required = false) String direction,
                                  RedirectAttributes ra) {
        // « aucune » détache explicitement ; une valeur absente est un oubli, pas un détachement
        if (direction == null || direction.isBlank()) {
            ra.addFlashAttribute("ko", "Choisissez la direction de rattachement.");
            return POSTES;
        }
        boolean detacher = "aucune".equals(direction);
        return executer(ra, POSTES, () -> {
            int nb = organisationService.rattacherPostes(postes, detacher ? null : direction);
            return detacher
                    ? nb + " poste(s) détaché(s) de leur direction."
                    : nb + " poste(s) rattaché(s) à la direction choisie.";
        });
    }

    @PostMapping("/postes/{id}/supprimer")
    public String supprimerPoste(@PathVariable int id, RedirectAttributes ra) {
        return executer(ra, POSTES, () ->
                "Poste « " + organisationService.supprimerPoste(id).getPoste().trim() + " » supprimé.");
    }

    /** Donne aux utilisateurs sélectionnés la direction de leur poste (gestion des utilisateurs : administrateur). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/utilisateurs/aligner")
    public String alignerUtilisateurs(@RequestParam(name = "utilisateurs", required = false) java.util.List<Integer> utilisateurs,
                                      RedirectAttributes ra) {
        return executer(ra, "redirect:/organisation?onglet=coherence", () ->
                organisationService.alignerSurDirectionDuPoste(utilisateurs)
                        + " utilisateur(s) placé(s) dans la direction de leur poste.");
    }

    /** Exécute l'action et affiche son message, ou l'erreur rencontrée, sur l'onglet concerné. */
    private static String executer(RedirectAttributes ra, String retour, Supplier<String> action) {
        try {
            ra.addFlashAttribute("ok", action.get());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("ko", e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            ra.addFlashAttribute("ko", "Cet élément est encore utilisé : il ne peut pas être supprimé.");
        }
        return retour;
    }
}
