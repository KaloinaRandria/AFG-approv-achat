package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.exception.FournisseurAlreadyExistsException;
import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.service.FournisseurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/fournisseur")
public class FournisseurController {
    private final FournisseurService fournisseurService;

    public FournisseurController(FournisseurService fournisseurService) {
        this.fournisseurService = fournisseurService;
    }

    @GetMapping("/add")
    public String addFournisseurPage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        return "fournisseur/fournisseur-saisie";
    }

    @PostMapping("/save")
    public String insertFournisseur(
            @RequestParam(name = "nom") String nomFournisseur,
            @RequestParam(name = "acronyme") String acronymeFournisseur,
            @RequestParam(name = "mail") String mailFournisseur,
            @RequestParam(name = "telephone") String contactFournisseur,
            @RequestParam(name = "description") String descriptionFournisseur,
            RedirectAttributes redirectAttributes) {

        try {
            Fournisseur fournisseur = new Fournisseur();
            fournisseur.setNom(nomFournisseur);
            fournisseur.setAcronyme(acronymeFournisseur);
            fournisseur.setMail(mailFournisseur);
            fournisseur.setContact(contactFournisseur);
            fournisseur.setDescription(descriptionFournisseur);

            fournisseurService.saveFournisseurIfNotExists(fournisseur);

            // Message succès
            redirectAttributes.addFlashAttribute(
                    "ok",
                    "Fournisseur enregistré avec succès."
            );

        } catch (FournisseurAlreadyExistsException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {
            // Cas : erreur technique
            redirectAttributes.addFlashAttribute(
                    "ko",
                    "Une erreur est survenue lors de l'enregistrement du fournisseur."
            );
        }

        // Redirection (PRG pattern)
        return "redirect:/fournisseur/saisie";
    }

}
