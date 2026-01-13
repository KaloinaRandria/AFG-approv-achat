package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.exception.FournisseurAlreadyExistsException;
import afg.achat.afgApprovAchat.model.Fournisseur;
import afg.achat.afgApprovAchat.service.FournisseurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@PreAuthorize("hasAnyRole('ADMIN','MOYENS_GENERAUX')")
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
        return "redirect:/fournisseur/add";
    }

    @GetMapping("/list")
    public String getListFournisseur(Model model , HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        Fournisseur[] fournisseurs = fournisseurService.getAllFournisseurs();
        model.addAttribute("fournisseurs", fournisseurs);
        return "fournisseur/fournisseur-liste";
    }

    @PostMapping("/modifier")
    public String modifierFournisseur(@RequestParam(name = "id") int id,
                                      @RequestParam(name = "nom") String nom,
                                      @RequestParam(name = "acronyme") String acronyme,
                                      @RequestParam(name = "mail") String mail,
                                      @RequestParam(name = "telephone") String contact,
                                      @RequestParam(name = "description", required = false) String description,
                                      RedirectAttributes redirectAttributes) {
        try {
            // Validation des données (obligatoires)
            if (id <= 0) {
                throw new IllegalArgumentException("ID fournisseur invalide");
            }
            if (nom == null || nom.trim().isEmpty()) {
                throw new IllegalArgumentException("Le nom du fournisseur est obligatoire");
            }
            if (acronyme == null || acronyme.trim().isEmpty()) {
                throw new IllegalArgumentException("L'acronyme est obligatoire");
            }
            if (mail == null || mail.trim().isEmpty()) {
                throw new IllegalArgumentException("Le mail est obligatoire");
            }
            if (contact == null || contact.trim().isEmpty()) {
                throw new IllegalArgumentException("Le téléphone est obligatoire");
            }

            // Appel du service de modification
            Fournisseur fournisseurModifie = fournisseurService.modifierFournisseur(
                    id,
                    nom.trim(),
                    acronyme.trim(),
                    mail.trim(),
                    contact.trim(),
                    (description != null ? description.trim() : null)
            );

            // Message succès
            redirectAttributes.addFlashAttribute("ok",
                    "Fournisseur <strong>" + fournisseurModifie.getNom() + "</strong> modifié avec succès !");
        } catch (IllegalArgumentException e) {
            // Erreur validation
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur de validation : " + e.getMessage());

            // Remettre les valeurs pour réaffichage dans la page
            redirectAttributes.addFlashAttribute("id", id);
            redirectAttributes.addFlashAttribute("nom", nom);
            redirectAttributes.addFlashAttribute("acronyme", acronyme);
            redirectAttributes.addFlashAttribute("mail", mail);
            redirectAttributes.addFlashAttribute("telephone", contact);
            redirectAttributes.addFlashAttribute("description", description);

        } catch (Exception e) {
            // Erreur technique
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur lors de la modification : " + e.getMessage());
            redirectAttributes.addFlashAttribute("id", id);
        }

        // Redirection vers la liste (comme article)
        return "redirect:/fournisseur/list";
    }
}
