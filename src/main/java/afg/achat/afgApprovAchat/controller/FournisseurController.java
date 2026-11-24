package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.FournisseurDTO;
import afg.achat.afgApprovAchat.exception.FournisseurAlreadyExistsException;
import afg.achat.afgApprovAchat.model.fournisseur.Fournisseur;
import afg.achat.afgApprovAchat.service.fournisseur.FournisseurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
        return "redirect:/fournisseur/list";
    }

    @GetMapping("/list")
    public String getListFournisseur(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String nom,
                                     @RequestParam(required = false) String mail,
                                     @RequestParam(required = false) String contact,
                                     Model model, HttpServletRequest request) {
        int taille = Math.min(Math.max(size, 5), 100);
        Page<Fournisseur> fournisseurs = fournisseurService.rechercher(nom, mail, contact,
                PageRequest.of(Math.max(page, 0), taille));

        model.addAttribute("fournisseurs", fournisseurs);
        model.addAttribute("nom", nom == null ? "" : nom);
        model.addAttribute("mail", mail == null ? "" : mail);
        model.addAttribute("contact", contact == null ? "" : contact);
        model.addAttribute("size", taille);
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));
        return "fournisseur/fournisseur-liste";
    }

    /** Recherche instantanée de la liste (index Lucene), même filtres que la page. */
    @GetMapping("/list/ajax")
    @ResponseBody
    public Page<FournisseurDTO> getListFournisseurAjax(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String nom,
                                                      @RequestParam(required = false) String mail,
                                                      @RequestParam(required = false) String contact) {
        int taille = Math.min(Math.max(size, 5), 100);
        return fournisseurService.rechercher(nom, mail, contact, PageRequest.of(Math.max(page, 0), taille))
                .map(FournisseurDTO::from);
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
