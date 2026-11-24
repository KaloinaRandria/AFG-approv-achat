package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.DTO.UtilisateurRequestDTO;
import afg.achat.afgApprovAchat.model.utilisateur.DemandeCompte;
import afg.achat.afgApprovAchat.model.utilisateur.Role;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.repository.utilisateur.PdpRepo;
import afg.achat.afgApprovAchat.repository.utilisateur.PosteRepo;
import afg.achat.afgApprovAchat.repository.util.RoleRepo;
import afg.achat.afgApprovAchat.repository.util.ServiceRepo;
import afg.achat.afgApprovAchat.service.securite.Habilitations;
import afg.achat.afgApprovAchat.service.utilisateur.DemandeCompteService;
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

import java.util.*;

/**
 * Gestion des utilisateurs, en maker / checker (domaine {@link Habilitations#IT}) :
 * <ul>
 *   <li>IT_MAKER saisit une création, une modification ou une (dés)activation : elle part en validation ;</li>
 *   <li>IT_CHECKER, une autre personne, la valide (elle est alors appliquée) ou la rejette avec un motif ;</li>
 *   <li>IT consulte ; ADMIN applique directement et peut aussi valider.</li>
 * </ul>
 * Le rôle ADMIN, et les comptes qui le portent, restent gérés par les seuls administrateurs.
 */
@Controller
@RequestMapping("/user")
@PreAuthorize("hasAnyRole('ADMIN','IT','IT_MAKER','IT_CHECKER')")
public class UtilisateurController {

    private static final String SAISIE = "T(afg.achat.afgApprovAchat.service.securite.Habilitations).peutCreer(authentication, 'IT')";
    private static final String VALIDATION = "T(afg.achat.afgApprovAchat.service.securite.Habilitations).peutValider(authentication, 'IT')";

    @Autowired private UtilisateurService   utilisateurService;
    @Autowired private DemandeCompteService demandeCompteService;
    @Autowired private RoleRepo             roleRepo;
    @Autowired private PosteRepo            posteRepo;
    @Autowired private ServiceRepo          serviceRepo;
    @Autowired private PdpRepo              pdpRepo;

    // -------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Utilisateur connecte() {
        Utilisateur principal = (Utilisateur) auth().getPrincipal();
        return utilisateurService.getUtilisateurByMail(principal.getMail());
    }

    /** Listes de choix et droits communs aux écrans. */
    private void populateChoix(Model model) {
        Authentication auth = auth();
        List<Role> roles = roleRepo.findAll(Sort.by("role"));
        boolean admin = Habilitations.estAdmin(auth);
        model.addAttribute("roles",    roles);   // filtres de la liste : tous les rôles
        // Formulaires : le rôle ADMIN n'est proposé qu'aux administrateurs
        model.addAttribute("rolesAttribuables", admin ? roles
                : roles.stream().filter(r -> !Habilitations.estRoleAdmin(r)).toList());
        model.addAttribute("connecteEstAdmin", admin);
        model.addAttribute("peutSaisir", Habilitations.peutCreer(auth, Habilitations.IT));
        model.addAttribute("peutValider", Habilitations.peutValider(auth, Habilitations.IT));
        // Saisie d'un maker : soumise à validation ; administrateur : appliquée directement
        model.addAttribute("avecValidation", !admin);
        model.addAttribute("nbDemandesEnAttente", demandeCompteService.nombreEnAttente());
        model.addAttribute("postes",   posteRepo.findAll(Sort.by("poste")));
        model.addAttribute("services", serviceRepo.findAll(Sort.by("libelle")));
        // Seuls les comptes actifs peuvent devenir supérieur ou validateur
        model.addAttribute("utilisateursActifs", utilisateurService.getUtilisateursActifs());
    }

    /** Vrai si la liste de rôles demandée contient le rôle ADMIN. */
    private boolean contientRoleAdmin(Collection<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return false;
        return roleRepo.findAllById(roleIds).stream().anyMatch(Habilitations::estRoleAdmin);
    }

    /**
     * Un non-administrateur ne peut ni attribuer le rôle ADMIN, ni modifier ou
     * (dés)activer un compte administrateur. Renvoie le motif du refus, ou {@code null}.
     */
    private String refusHorsAdmin(Utilisateur cible, Collection<Integer> roleIdsDemandes) {
        if (Habilitations.estAdmin(auth())) return null;
        if (Habilitations.estCompteAdmin(cible)) {
            return "Seul un administrateur peut modifier un compte administrateur.";
        }
        if (contientRoleAdmin(roleIdsDemandes)) {
            return "Seul un administrateur peut attribuer le rôle ADMIN.";
        }
        return null;
    }

    /** Une demande attend déjà pour ce compte : rien d'autre ne s'applique tant qu'elle n'est pas traitée. */
    private String refusDemandeEnAttente(int id) {
        return demandeCompteService.ciblesEnAttente().contains(id)
                ? "Une demande attend déjà une validation pour ce compte : elle doit être validée, rejetée ou annulée d'abord."
                : null;
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

    private static String messageSoumis(String quoi) {
        return quoi + " envoyée pour validation : elle sera appliquée après l'accord d'un valideur ("
                + Habilitations.roleChecker(Habilitations.IT) + ").";
    }

    // -------------------------------------------------------
    // Création
    // -------------------------------------------------------

    @PreAuthorize(SAISIE)
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

    @PreAuthorize(SAISIE)
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

        String refus = refusHorsAdmin(null, dto.getRoleIds());
        if (refus != null) {
            redirectAttributes.addFlashAttribute("ko", refus);
            redirectAttributes.addFlashAttribute("utilisateur", dto);
            return "redirect:/user/add";
        }

        boolean direct = Habilitations.estAdmin(auth());
        try {
            if (direct) {
                utilisateurService.insererUtilisateur(dto);
            } else {
                demandeCompteService.soumettreCreation(dto, connecte());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", e instanceof IllegalArgumentException
                    ? e.getMessage() : "Erreur inattendue : " + e.getMessage());
            // La saisie est conservée : rien à retaper
            redirectAttributes.addFlashAttribute("utilisateur", dto);
            return "redirect:/user/add";
        }

        redirectAttributes.addFlashAttribute("ok", direct
                ? "Utilisateur '" + dto.getPrenom() + " " + dto.getNom() + "' créé avec succès."
                : messageSoumis("Création du compte de " + dto.getPrenom() + " " + dto.getNom()));
        if ("nouveau".equals(ensuite)) {
            return "redirect:/user/add";
        }
        if (!direct) {
            return "redirect:/user/demandes";
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
        model.addAttribute("ciblesEnAttente", demandeCompteService.ciblesEnAttente());

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
     * Modifie un utilisateur (informations, rôles, supérieur, validateurs) : directement pour
     * un administrateur, sinon par une demande à valider. Revient sur la liste (mêmes filtres et page).
     */
    @PreAuthorize(SAISIE)
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
        dto.setNom(nom == null ? null : nom.trim());
        dto.setPrenom(prenom == null ? null : prenom.trim());
        dto.setMail(mail == null ? null : mail.trim());
        dto.setContact(contact);
        dto.setPosteId(posteId);
        dto.setServiceId(serviceId);
        dto.setSuperieurHierarchiqueId(superieurHierarchiqueId);
        dto.setRoleIds(roleIdsList != null ? new HashSet<>(roleIdsList) : new HashSet<>());
        dto.setPdpId(existant.getPdp() != null ? existant.getPdp().getId() : null);
        // Le formulaire envoie toujours la liste complète : vide = aucun validateur
        dto.setValidateurIds(validateurIdsList != null ? new HashSet<>(validateurIdsList) : new HashSet<>());

        String refus = Optional.ofNullable(refusHorsAdmin(existant, dto.getRoleIds()))
                .orElse(refusDemandeEnAttente(id));
        if (refus != null) {
            redirectAttributes.addFlashAttribute("ko", refus);
            return retourListe(returnUrl);
        }

        try {
            if (Habilitations.estAdmin(auth())) {
                utilisateurService.modifierUtilisateur(id, dto);
                redirectAttributes.addFlashAttribute("ok",
                        "Utilisateur '" + dto.getPrenom() + " " + dto.getNom() + "' modifié avec succès.");
            } else {
                demandeCompteService.soumettreModification(id, dto, connecte());
                redirectAttributes.addFlashAttribute("ok",
                        messageSoumis("Modification du compte de " + existant.getPrenom() + " " + existant.getNom()));
            }
        } catch (IllegalArgumentException | EntityNotFoundException e) {
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
     * Désactive (collaborateur parti) ou réactive un compte : directement pour un administrateur,
     * sinon par une demande à valider. Un compte inactif ne peut plus se connecter ; son historique reste.
     */
    @PreAuthorize(SAISIE)
    @PostMapping("/{id}/statut")
    public String changerStatut(@PathVariable int id,
                                @RequestParam boolean actif,
                                @RequestParam(value = "returnUrl", required = false) String returnUrl,
                                RedirectAttributes redirectAttributes) {
        Utilisateur auteur = connecte();

        String refus = Optional.ofNullable(refusHorsAdmin(utilisateurService.getUtilisateurById(id), null))
                .orElse(refusDemandeEnAttente(id));
        if (refus != null) {
            redirectAttributes.addFlashAttribute("ko", refus);
            return retourListe(returnUrl);
        }

        try {
            if (Habilitations.estAdmin(auth())) {
                Utilisateur u = utilisateurService.changerStatut(id, actif, auteur != null ? auteur.getId() : -1);
                redirectAttributes.addFlashAttribute("ok", "Compte de " + u.getPrenom() + " " + u.getNom()
                        + (actif ? " réactivé." : " désactivé : il ne peut plus se connecter."));
            } else {
                DemandeCompte d = demandeCompteService.soumettreStatut(id, actif, auteur);
                redirectAttributes.addFlashAttribute("ok", messageSoumis(
                        (actif ? "Réactivation" : "Désactivation") + " du compte " + d.getLibelleCible()));
            }
        } catch (EntityNotFoundException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return retourListe(returnUrl);
    }

    // -------------------------------------------------------
    // Demandes de compte (maker / checker)
    // -------------------------------------------------------

    /** Demandes en attente (avec ce qui change) et dernières décisions. */
    @GetMapping("/demandes")
    public String demandes(Model model) {
        Authentication auth = auth();
        Utilisateur courant = connecte();
        List<DemandeCompte> enAttente = demandeCompteService.enAttente();

        Map<Integer, List<DemandeCompteService.Changement>> changements = new HashMap<>();
        Map<Integer, Boolean> decidables = new HashMap<>();
        Map<Integer, Boolean> annulables = new HashMap<>();
        for (DemandeCompte d : enAttente) {
            changements.put(d.getId(), demandeCompteService.changements(d));
            decidables.put(d.getId(), demandeCompteService.peutDecider(d, courant, auth));
            annulables.put(d.getId(), d.getDemandeur() != null && d.getDemandeur().getId() == courant.getId());
        }

        model.addAttribute("enAttente", enAttente);
        model.addAttribute("changements", changements);
        model.addAttribute("decidables", decidables);
        model.addAttribute("annulables", annulables);
        model.addAttribute("historique", demandeCompteService.historique());
        model.addAttribute("roleMaker", Habilitations.roleMaker(Habilitations.IT));
        model.addAttribute("roleChecker", Habilitations.roleChecker(Habilitations.IT));
        populateChoix(model);
        return "utilisateur/demandes-compte";
    }

    @PreAuthorize(VALIDATION)
    @PostMapping("/demandes/{id}/valider")
    public String validerDemande(@PathVariable int id, RedirectAttributes redirectAttributes) {
        try {
            DemandeCompte d = demandeCompteService.valider(id, connecte(), auth());
            redirectAttributes.addFlashAttribute("ok", d.getType().getLibelle() + " du compte "
                    + d.getLibelleCible() + " validée et appliquée.");
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/user/demandes";
    }

    @PreAuthorize(VALIDATION)
    @PostMapping("/demandes/{id}/rejeter")
    public String rejeterDemande(@PathVariable int id,
                                 @RequestParam(value = "motif", required = false) String motif,
                                 RedirectAttributes redirectAttributes) {
        try {
            DemandeCompte d = demandeCompteService.rejeter(id, connecte(), auth(), motif);
            redirectAttributes.addFlashAttribute("ok", d.getType().getLibelle() + " du compte "
                    + d.getLibelleCible() + " rejetée.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/user/demandes";
    }

    @PreAuthorize(SAISIE)
    @PostMapping("/demandes/{id}/annuler")
    public String annulerDemande(@PathVariable int id, RedirectAttributes redirectAttributes) {
        try {
            DemandeCompte d = demandeCompteService.annuler(id, connecte());
            redirectAttributes.addFlashAttribute("ok", "Demande annulée : " + d.getType().getLibelle().toLowerCase()
                    + " du compte " + d.getLibelleCible() + ".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
        }
        return "redirect:/user/demandes";
    }
}
