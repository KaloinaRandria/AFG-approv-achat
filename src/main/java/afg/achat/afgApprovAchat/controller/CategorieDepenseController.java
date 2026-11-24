package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.CategorieDepense;
import afg.achat.afgApprovAchat.service.demande.CategorieDepenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Paramétrage des catégories des demandes Autres : Moyens Généraux et administrateur. */
@Controller
@RequestMapping("/categorie-depense")
@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
@RequiredArgsConstructor
public class CategorieDepenseController {

    private static final String PAGE = "redirect:/categorie-depense";

    private final CategorieDepenseService categorieDepenseService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("categories", categorieDepenseService.getToutes());
        model.addAttribute("nbDemandes", categorieDepenseService.nombreDeDemandesParCategorie());
        return "categorie/categorie-depense-liste";
    }

    @PostMapping("/ajouter")
    public String ajouter(@RequestParam(name = "libelle", required = false) String libelle,
                          @RequestParam(name = "beneficiaireObligatoire", defaultValue = "false") boolean beneficiaireObligatoire,
                          @RequestParam(name = "ordre", required = false) String ordre,
                          RedirectAttributes redirectAttributes) {
        try {
            CategorieDepense c = categorieDepenseService.creer(libelle, beneficiaireObligatoire, ordre);
            redirectAttributes.addFlashAttribute("ok", "Catégorie « " + c.getLibelle() + " » ajoutée.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/{id}/modifier")
    public String modifier(@PathVariable int id,
                           @RequestParam(name = "libelle", required = false) String libelle,
                           @RequestParam(name = "beneficiaireObligatoire", defaultValue = "false") boolean beneficiaireObligatoire,
                           @RequestParam(name = "ordre", required = false) String ordre,
                           RedirectAttributes redirectAttributes) {
        try {
            CategorieDepense c = categorieDepenseService.modifier(id, libelle, beneficiaireObligatoire, ordre);
            redirectAttributes.addFlashAttribute("ok", "Catégorie « " + c.getLibelle() + " » modifiée.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/{id}/activation")
    public String activation(@PathVariable int id,
                             @RequestParam(name = "actif") boolean actif,
                             RedirectAttributes redirectAttributes) {
        try {
            CategorieDepense c = categorieDepenseService.changerActivation(id, actif);
            redirectAttributes.addFlashAttribute("ok", "Catégorie « " + c.getLibelle() + " » "
                    + (actif ? "réactivée : elle est de nouveau proposée à la saisie."
                             : "désactivée : elle n'est plus proposée à la saisie."));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return PAGE;
    }
}
