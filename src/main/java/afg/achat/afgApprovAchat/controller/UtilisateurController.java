package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.UtilisateurRequestDTO;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.PdpRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.UtilisateurRepo;
import afg.achat.afgApprovAchat.repository.util.RoleRepo;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasAnyRole('ADMIN')")
public class UtilisateurController {

    @Autowired private UtilisateurService utilisateurService;
    @Autowired private RoleRepo           roleRepo;
    @Autowired private PosteRepo          posteRepo;
    @Autowired private ServiceRepo        serviceRepo;
    @Autowired private PdpRepo            pdpRepo;
    @Autowired private UtilisateurRepo    utilisateurRepo;

    // -------------------------------------------------------
    // Utilitaire : alimente les listes du formulaire
    // -------------------------------------------------------
    private void populateFormModel(Model model) {
        model.addAttribute("roles",        roleRepo.findAll());
        model.addAttribute("postes",       posteRepo.findAll());
        model.addAttribute("services",     serviceRepo.findAll());
        model.addAttribute("pdps",         pdpRepo.findAll());
        model.addAttribute("utilisateurs", utilisateurRepo.findAll());
    }

    @GetMapping("/add")
    public String addUserPage(Model model) {
        populateFormModel(model);
        model.addAttribute("utilisateur", new UtilisateurRequestDTO()); // ← manquant
        return "utilisateur/utilisateur-saisie";
    }

    // -------------------------------------------------------
    // POST /user/save
    // -------------------------------------------------------
    @PostMapping("/save")
    public String insertUser(
            @RequestParam("nom")    String nom,
            @RequestParam("prenom") String prenom,
            @RequestParam("mail")   String mail,

            @RequestParam(value = "contact",                 required = false) String  contact,
            @RequestParam(value = "posteId",                 required = false) Integer posteId,
            @RequestParam(value = "serviceId",               required = false) Integer serviceId,
            @RequestParam(value = "pdpId",                   required = false) Integer pdpId,
            @RequestParam(value = "superieurHierarchiqueId", required = false) Integer superieurHierarchiqueId,

            // Select2 multi → Spring reçoit une List, on convertit en Set
            @RequestParam(value = "roles",       required = false) List<Integer> roleIdsList,
            @RequestParam(value = "validateurs", required = false) List<Integer> validateurIdsList,

            RedirectAttributes redirectAttributes
    ) {
        UtilisateurRequestDTO dto = new UtilisateurRequestDTO();
        dto.setNom(nom);
        dto.setPrenom(prenom);
        dto.setMail(mail);
        dto.setContact(contact);
        dto.setPosteId(posteId);
        dto.setServiceId(serviceId);
        dto.setPdpId(pdpId);
        dto.setSuperieurHierarchiqueId(superieurHierarchiqueId);

        // Conversion List → Set (null-safe)
        dto.setRoleIds(roleIdsList != null ? new HashSet<>(roleIdsList) : new HashSet<>());
        dto.setValidateurIds(validateurIdsList != null ? new HashSet<>(validateurIdsList) : null);

        try {
            utilisateurService.insererUtilisateur(dto);
            redirectAttributes.addFlashAttribute("ok",
                    "Utilisateur '" + prenom + " " + nom + "' créé avec succès.");
            return "redirect:/user/add";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/user/add";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko",
                    "Erreur inattendue : " + e.getMessage());
            return "redirect:/user/add";
        }
    }

    /**
     * Liste des utilisateurs avec pagination et filtres
     *
     * Paramètres optionnels :
     * - nom : filtre sur le nom
     * - prenom : filtre sur le prénom
     * - mail : filtre sur l'email
     * - service : filtre sur le service
     * - page : numéro de page (0-indexed)
     * - size : taille de la page (10, 25, 50, 100)
     * - sort : champ de tri (nom, prenom, mail, contact)
     * - dir : direction du tri (ASC, DESC)
     */
    @GetMapping("/list")
    public String listUtilisateurs(
            @RequestParam(defaultValue = "") String nom,
            @RequestParam(defaultValue = "") String prenom,
            @RequestParam(defaultValue = "") String mail,
            @RequestParam(defaultValue = "") String service,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "nom") String sort,
            @RequestParam(defaultValue = "ASC") String dir,
            Model model) {

        //Appel au service avec les filtres
        Page<Utilisateur> pageResult = utilisateurService.rechercherUtilisateurs(
                nom, prenom, mail, service, page, size, sort, dir
        );

        // Ajout de la page et des données au modèle
        model.addAttribute("utilisateurs", pageResult.getContent());
        model.addAttribute("utilisateurPage", pageResult);

        // Filtres appliqués (pour afficher dans le formulaire)
        model.addAttribute("nom", nom);
        model.addAttribute("prenom", prenom);
        model.addAttribute("mail", mail);
        model.addAttribute("service", service);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("size", size);

        // Données pour les dropdowns (modales)
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("postes", posteRepo.findAll());

        return "utilisateur/utilisateur-liste";
    }

    /**
     * Recherche textuelle simple
     */
    @GetMapping("/search")
    public String searchUtilisateurs(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        if (q == null || q.isEmpty()) {
            // Si pas de terme, afficher tous
            Page<Utilisateur> pageResult = utilisateurService.getAllUtilisateurs(page, size);
            model.addAttribute("utilisateurs", pageResult.getContent());
            model.addAttribute("utilisateurPage", pageResult);
        } else {
            // Recherche textuelle
            Page<Utilisateur> pageResult = utilisateurService.searchUtilisateurs(q, page, size);
            model.addAttribute("utilisateurs", pageResult.getContent());
            model.addAttribute("utilisateurPage", pageResult);
            model.addAttribute("searchTerm", q);
        }

        // Données pour les dropdowns (modales)
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("postes", posteRepo.findAll());

        return "utilisateur/utilisateur-liste";
    }
}