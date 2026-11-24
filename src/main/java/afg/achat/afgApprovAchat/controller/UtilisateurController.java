package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.UtilisateurRequestDTO;
import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.PdpRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import afg.achat.afgApprovAchat.repository.util.RoleRepo;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasAnyRole('ADMIN')")
public class UtilisateurController {

    @Autowired private UtilisateurService utilisateurService;
    @Autowired private RoleRepo           roleRepo;
    @Autowired private PosteRepo          posteRepo;
    @Autowired private ServiceRepo        serviceRepo;
    @Autowired private PdpRepo            pdpRepo;

    // -------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------

    /** Listes de choix communes à la saisie et à la modification. */
    private void populateChoix(Model model) {
        model.addAttribute("roles",    roleRepo.findAll(Sort.by("role")));
        model.addAttribute("postes",   posteRepo.findAll(Sort.by("poste")));
        model.addAttribute("services", serviceRepo.findAll(Sort.by("libelle")));
        // Seuls les comptes actifs peuvent devenir supérieur ou validateur
        model.addAttribute("utilisateursActifs", utilisateurService.getUtilisateursActifs());
    }

    private static String libelle(Utilisateur u) {
        return u.getNom() + " " + u.getPrenom() + " (" + u.getMail() + ")"
                + (u.isActif() ? "" : " — inactif");
    }

    /** Retour vers la liste en conservant filtres et page ; jamais vers une autre adresse. */
    private static String retourListe(String returnUrl) {
        boolean sur = returnUrl != null && returnUrl.startsWith("/user/list")
                && !returnUrl.contains("//") && !returnUrl.contains("\\");
        return "redirect:" + (sur ? returnUrl : "/user/list");
    }

    // -------------------------------------------------------
    // Création
    // -------------------------------------------------------

    @GetMapping("/add")
    public String addUserPage(Model model) {
        populateChoix(model);
        model.addAttribute("pdps", pdpRepo.findAll());
        // Après une erreur, la saisie est renvoyée en flash : on la réaffiche telle quelle
        if (!model.containsAttribute("utilisateur")) {
            model.addAttribute("utilisateur", new UtilisateurRequestDTO());
        }
        return "utilisateur/utilisateur-saisie";
    }

    /**
     * Vérification immédiate de l'email pendant la saisie :
     * indique si un compte (actif ou non) l'utilise déjà.
     */
    @GetMapping("/mail-disponible")
    @ResponseBody
    public Map<String, Object> mailDisponible(@RequestParam String mail) {
        Utilisateur existant = mail == null || mail.isBlank()
                ? null : utilisateurService.getUtilisateurByMail(mail.trim());
        Map<String, Object> rep = new HashMap<>();
        rep.put("disponible", existant == null);
        if (existant != null) {
            rep.put("nom", existant.getPrenom() + " " + existant.getNom());
            rep.put("actif", existant.isActif());
        }
        return rep;
    }

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

            // "nouveau" : rester sur le formulaire pour enchaîner une autre création
            @RequestParam(value = "ensuite", required = false) String ensuite,

            RedirectAttributes redirectAttributes
    ) {
        UtilisateurRequestDTO dto = new UtilisateurRequestDTO();
        dto.setNom(nom == null ? null : nom.trim());
        dto.setPrenom(prenom == null ? null : prenom.trim());
        dto.setMail(mail == null ? null : mail.trim());
        dto.setContact(contact);
        dto.setPosteId(posteId);
        dto.setServiceId(serviceId);
        dto.setPdpId(pdpId);
        dto.setSuperieurHierarchiqueId(superieurHierarchiqueId);
        dto.setRoleIds(roleIdsList != null ? new HashSet<>(roleIdsList) : new HashSet<>());
        dto.setValidateurIds(validateurIdsList != null ? new HashSet<>(validateurIdsList) : null);

        try {
            utilisateurService.insererUtilisateur(dto);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", e instanceof IllegalArgumentException
                    ? e.getMessage() : "Erreur inattendue : " + e.getMessage());
            // La saisie est conservée : rien à retaper
            redirectAttributes.addFlashAttribute("utilisateur", dto);
            return "redirect:/user/add";
        }

        redirectAttributes.addFlashAttribute("ok",
                "Utilisateur '" + dto.getPrenom() + " " + dto.getNom() + "' créé avec succès.");
        if ("nouveau".equals(ensuite)) {
            return "redirect:/user/add";
        }
        // Sinon : la liste affiche directement le compte créé
        redirectAttributes.addAttribute("q", dto.getMail());
        redirectAttributes.addAttribute("statut", "TOUS");
        return "redirect:/user/list";
    }

    // -------------------------------------------------------
    // Liste
    // -------------------------------------------------------

    /**
     * Liste des utilisateurs : recherche (nom, prénom, email), service, rôle, statut,
     * tri par colonne et pagination. Par défaut, seuls les comptes actifs sont affichés.
     */
    @GetMapping("/list")
    public String listUtilisateurs(
            @RequestParam(defaultValue = "")       String  q,
            @RequestParam(required = false)        Integer serviceId,
            @RequestParam(required = false)        Integer roleId,
            @RequestParam(defaultValue = "ACTIFS") String  statut,
            @RequestParam(defaultValue = "0")      int     page,
            @RequestParam(defaultValue = "10")     int     size,
            @RequestParam(defaultValue = "nom")    String  sort,
            @RequestParam(defaultValue = "ASC")    String  dir,
            HttpServletRequest request,
            Model model) {

        Boolean actif = switch (statut.toUpperCase()) {
            case "INACTIFS" -> Boolean.FALSE;
            case "TOUS"     -> null;
            default         -> Boolean.TRUE;
        };
        String statutNormalise = actif == null ? "TOUS" : (actif ? "ACTIFS" : "INACTIFS");

        Page<Utilisateur> pageResult = utilisateurService.rechercherUtilisateurs(
                q, serviceId, roleId, actif, page, size, sort, dir);

        model.addAttribute("utilisateurs", pageResult.getContent());
        model.addAttribute("utilisateurPage", pageResult);

        model.addAttribute("q", q);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("roleId", roleId);
        model.addAttribute("statut", statutNormalise);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", "DESC".equalsIgnoreCase(dir) ? "DESC" : "ASC");
        model.addAttribute("size", size);

        Map<String, String> statuts = new LinkedHashMap<>();
        statuts.put("ACTIFS", "Actifs");
        statuts.put("INACTIFS", "Inactifs");
        statuts.put("TOUS", "Tous");
        model.addAttribute("statuts", statuts);

        String qs = request.getQueryString();
        model.addAttribute("returnUrl", "/user/list" + (qs == null ? "" : "?" + qs));
        model.addAttribute("urlPagination", PaginationUrl.sansPage(request));

        populateChoix(model);
        return "utilisateur/utilisateur-liste";
    }

    // -------------------------------------------------------
    // Modification
    // -------------------------------------------------------

    /**
     * Données d'un utilisateur pour préremplir la fenêtre de modification.
     * Le supérieur et les validateurs sont renvoyés avec leur libellé : s'ils sont
     * inactifs, ils ne figurent pas dans les listes de choix et sont ajoutés côté page.
     */
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getUtilisateur(@PathVariable int id) {
        Utilisateur utilisateur = utilisateurService.getUtilisateurById(id);
        if (utilisateur == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", utilisateur.getId());
        data.put("nom", utilisateur.getNom());
        data.put("prenom", utilisateur.getPrenom());
        data.put("mail", utilisateur.getMail());
        data.put("contact", utilisateur.getContact());
        data.put("actif", utilisateur.isActif());
        data.put("serviceId", utilisateur.getService() != null ? utilisateur.getService().getId() : null);
        data.put("posteId", utilisateur.getPoste() != null ? utilisateur.getPoste().getId() : null);

        Utilisateur sup = utilisateur.getSuperieurHierarchique();
        data.put("superieur", sup == null ? null : Map.of("id", sup.getId(), "libelle", libelle(sup)));

        data.put("roleIds", utilisateur.getRoles().stream().map(Role::getId).toList());
        data.put("validateurs", utilisateur.getValidateurs().stream()
                .sorted(Comparator.comparing(Utilisateur::getNom, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(v -> Map.of("id", v.getId(), "libelle", libelle(v)))
                .toList());

        return ResponseEntity.ok(data);
    }

    /**
     * Met à jour un utilisateur : informations, rôles, supérieur et validateurs.
     * Revient sur la liste avec les mêmes filtres et la même page.
     */
    @PostMapping("/modifier")
    public String updateUser(
            @RequestParam("id") int id,
            @RequestParam("nom") String nom,
            @RequestParam("prenom") String prenom,
            @RequestParam("mail") String mail,

            @RequestParam(value = "contact", required = false) String contact,
            @RequestParam(value = "posteId", required = false) Integer posteId,
            @RequestParam(value = "serviceId", required = false) Integer serviceId,
            @RequestParam(value = "superieurHierarchiqueId", required = false) Integer superieurHierarchiqueId,
            @RequestParam(value = "roles", required = false) List<Integer> roleIdsList,
            @RequestParam(value = "validateurs", required = false) List<Integer> validateurIdsList,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            RedirectAttributes redirectAttributes
    ) {
        Utilisateur existant = utilisateurService.getUtilisateurById(id);
        if (existant == null) {
            redirectAttributes.addFlashAttribute("ko", "Utilisateur introuvable : id=" + id);
            return retourListe(returnUrl);
        }

        UtilisateurRequestDTO dto = new UtilisateurRequestDTO();
        dto.setNom(nom);
        dto.setPrenom(prenom);
        dto.setMail(mail);
        dto.setContact(contact);
        dto.setPosteId(posteId);
        dto.setServiceId(serviceId);
        dto.setSuperieurHierarchiqueId(superieurHierarchiqueId);
        dto.setRoleIds(roleIdsList != null ? new HashSet<>(roleIdsList) : new HashSet<>());
        dto.setPdpId(existant.getPdp() != null ? existant.getPdp().getId() : null);
        // Le formulaire envoie toujours la liste complète : vide = aucun validateur
        dto.setValidateurIds(validateurIdsList != null ? new HashSet<>(validateurIdsList) : new HashSet<>());

        try {
            utilisateurService.modifierUtilisateur(id, dto);
            redirectAttributes.addFlashAttribute("ok",
                    "Utilisateur '" + prenom + " " + nom + "' modifié avec succès.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur inattendue : " + e.getMessage());
        }
        return retourListe(returnUrl);
    }

    // -------------------------------------------------------
    // Statut actif / inactif
    // -------------------------------------------------------

    /**
     * Désactive (collaborateur parti) ou réactive un compte.
     * Un compte inactif ne peut plus se connecter ni recevoir de tâches ; son historique reste.
     */
    @PostMapping("/{id}/statut")
    public String changerStatut(@PathVariable int id,
                                @RequestParam boolean actif,
                                @RequestParam(value = "returnUrl", required = false) String returnUrl,
                                RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur auteur = utilisateurService.getUtilisateurByMail(principal.getMail());

        try {
            Utilisateur u = utilisateurService.changerStatut(id, actif, auteur != null ? auteur.getId() : -1);
            redirectAttributes.addFlashAttribute("ok", "Compte de " + u.getPrenom() + " " + u.getNom()
                    + (actif ? " réactivé." : " désactivé : il ne peut plus se connecter."));
        } catch (EntityNotFoundException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return retourListe(returnUrl);
    }
}
