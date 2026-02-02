package afg.achat.afgApprovAchat.controller;

import afg.achat.afgApprovAchat.model.demande.DemandeFille;
import afg.achat.afgApprovAchat.model.demande.DemandeMere;
import afg.achat.afgApprovAchat.model.util.StatutDemande;
import afg.achat.afgApprovAchat.model.utilisateur.Utilisateur;
import afg.achat.afgApprovAchat.service.ArticleService;
import afg.achat.afgApprovAchat.service.CentreBudgetaireService;
import afg.achat.afgApprovAchat.service.demande.DemandeFilleService;
import afg.achat.afgApprovAchat.service.demande.DemandeMereService;
import afg.achat.afgApprovAchat.service.util.AdresseService;
import afg.achat.afgApprovAchat.service.util.DepartementService;
import afg.achat.afgApprovAchat.service.util.IdGenerator;
import afg.achat.afgApprovAchat.service.utilisateur.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/demande")
public class DemandeController {
    @Autowired
    AdresseService adresseService;
    @Autowired
    DepartementService departementService;
    @Autowired
    DemandeMereService demandeMereService;
    @Autowired
    DemandeFilleService demandeFilleService;
    @Autowired
    ArticleService articleService;
    @Autowired
    UtilisateurService utilisateurService;
    @Autowired
    IdGenerator idGenerator;
    @Autowired
    CentreBudgetaireService centreBudgetaireService;

    @GetMapping("/add")
    public String addDemandePage(Model model, HttpServletRequest request) {
        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("priorites", DemandeMere.PrioriteDemande.values());
        model.addAttribute("ligneBudgetaires",centreBudgetaireService.getAllCentreBudgetaires() );

        return "demande/demande-saisie";
    }

    @PostMapping("/save")
    public String insertDemande(@RequestParam(name = "dateSortie") String dateSortie,
                                @RequestParam(name = "motif") String motif,
                                @RequestParam(name = "description") String description,
                                @RequestParam(name = "articleCodes[]") List<String> articleCodes,
                                @RequestParam(name = "quantite[]") List<String> quantite,
                                RedirectAttributes redirectAttributes) {

        Utilisateur user = (Utilisateur) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(user.getMail());
         
        try {
            if (dateSortie == null || dateSortie.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "La date prévue pour la livraison est obligatoire.");
                return "redirect:/demande/add";
            }
            if (motif == null || motif.isEmpty()) {
                redirectAttributes.addFlashAttribute("ko", "Le motif evoqué est obligatoire.");
                return "redirect:/demande/add";
            }
            DemandeMere demandeMere = new DemandeMere();
            demandeMere.setId(idGenerator);
            demandeMere.setDateDemande(String.valueOf(LocalDateTime.now()));
            demandeMere.setDateSortie(dateSortie);
            demandeMere.setMotifEvoque(motif);
            demandeMere.setDemandeur(utilisateur);
            demandeMere.setDescription(description);
            demandeMere.setStatut(1);

            this.demandeMereService.saveDemandeMere(demandeMere);

            List<DemandeFille> demandeFilles = new ArrayList<>();
            for (int i = 0; i < articleCodes.size(); i++) {
                DemandeFille demandeFille = new DemandeFille();
                demandeFille.setDemandeMere(demandeMere);
                int finalI = i;
                demandeFille.setArticle(articleService.getArticleByCodeArticle(articleCodes.get(i))
                        .orElseThrow(() -> new IllegalArgumentException("Article introuvable : " + articleCodes.get(finalI))));
                demandeFille.setQuantite(quantite.get(i));
                demandeFille.setStatut(1);
                demandeFilles.add(demandeFille);

                this.demandeFilleService.saveDemandeFille(demandeFille);
            }

            if (articleCodes.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Impossible de valider la demande sans aucune ligne d’article."
                );
                return "redirect:/demande/add";
            }

            boolean hasValideLine = false;
            for (int i = 0; i < articleCodes.size(); i++) {
                if (quantite.get(i) != null && !quantite.get(i).isEmpty()) {
                    try {
                        double qte = Double.parseDouble(quantite.get(i));
                        if (qte > 0) {
                            hasValideLine = true;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (!hasValideLine) {
                redirectAttributes.addFlashAttribute(
                        "ko",
                        "Veuillez saisir au moins une ligne avec une quantité reçue valide."
                );
                return "redirect:/demande/add";
            }

            redirectAttributes.addFlashAttribute("ok", "Demande enregistrée avec succès.");
            return "redirect:/demande/list";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("ko", e.getMessage());
            return "redirect:/demande/add";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("ko", "Erreur lors de l'enregistrement de la demande : " + e.getMessage());
            return "redirect:/demande/add";
        }

    }

    @GetMapping("/list")
    public String listDemandePage(Model model,
                                  HttpServletRequest request,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "dateDemande") String sort,
                                  @RequestParam(defaultValue = "desc") String dir,

                                  // ✅ statut optionnel (0 ou null = Tous)
                                  @RequestParam(required = false) Integer statut,

                                  @RequestParam(required = false) String num,
                                  @RequestParam(required = false) String demandeur,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                  @RequestParam(required = false, defaultValue = "ALL") String scope) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isMG = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MOYENS_GENERAUX"));
        boolean isAdminOrMG = isAdmin || isMG;


        // ✅ statutLabels (affichage table)
        Map<Integer, String> statutLabels = Map.of(
                StatutDemande.CREE, "CREE",
                StatutDemande.VALIDATION_N1, "EN_VALIDATION",
                StatutDemande.VALIDATION_N2, "EN_VALIDATION",
                StatutDemande.VALIDATION_N3, "EN_VALIDATION",
                StatutDemande.VALIDE, "VALIDE",
                StatutDemande.REFUSE, "REFUSE"
        );

        // ✅ filtre statut (select)
        Map<Integer, String> statutFiltre = new LinkedHashMap<>();
        statutFiltre.put(StatutDemande.CREE, "CREE");
        statutFiltre.put(StatutDemande.VALIDATION_N1, "EN_VALIDATION");
        statutFiltre.put(StatutDemande.VALIDE, "VALIDE");
        statutFiltre.put(StatutDemande.REFUSE, "REFUSE");

        model.addAttribute("statutFiltre", statutFiltre);

        //normalisation : 0 ou null => pas de filtre
        Integer statutFilter = (statut == null || statut == 0) ? null : statut;

        //MG voit uniquement VALIDATION_N1 (11)
        if (isMG && !isAdmin) {
            statutFilter = StatutDemande.VALIDATION_N1;
        }

        List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(utilisateur.getId());
        boolean hasChildren = visibleIds.size() > 1;

        boolean showDemandeurColumn = isAdminOrMG || hasChildren;
        model.addAttribute("showDemandeurColumn", showDemandeurColumn);

        boolean showScopeFilter = hasChildren && !isAdminOrMG;
        model.addAttribute("showDemandeurScopeFilter", showScopeFilter);

        // calcule ids selon scope
        List<Integer> idsToUse = visibleIds; // ALL par défaut (moi + enfants)
        if (!isAdminOrMG) {
            if ("ME".equalsIgnoreCase(scope)) {
                idsToUse = List.of(utilisateur.getId());
            } else if ("CHILDREN".equalsIgnoreCase(scope)) {
                idsToUse = visibleIds.stream()
                        .filter(id -> !id.equals(utilisateur.getId()))
                        .toList();
            }
        }

        var demandesMeres = isAdminOrMG
                ? demandeMereService.searchDemandes(num, demandeur, type, statutFilter, dateFrom, dateTo, page, size, sort, dir)
                : (idsToUse.isEmpty()
                ? demandeMereService.searchDemandesVisibleParUtilisateur(num, demandeur, type, statutFilter, dateFrom, dateTo, List.of(-1), page, size, sort, dir)
                : demandeMereService.searchDemandesVisibleParUtilisateur(num, demandeur, type, statutFilter, dateFrom, dateTo, idsToUse, page, size, sort, dir)
        );

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demandesMeres", demandesMeres);

        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);

        model.addAttribute("num", num == null ? "" : num);
        model.addAttribute("demandeur", demandeur == null ? "" : demandeur);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("dateFrom", dateFrom);
        model.addAttribute("dateTo", dateTo);
        model.addAttribute("scope", scope);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());
        model.addAttribute("statutLabels", statutLabels);
        model.addAttribute("isMGOnly", isMG);

        // ✅ pour garder la sélection dans le select
        model.addAttribute("statut", (statut == null) ? 0 : statut);


        return "demande/demande-liste";
    }

    @PostMapping("/fiche/{id}/type-demande")
    public String updateTypeDemande(@PathVariable("id") String id,
                                    @RequestParam(value = "typeDemande", required = false) String typeDemande,
                                    RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdminOrMG = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_MOYENS_GENERAUX")
        );

        if (!isAdminOrMG) {
            redirectAttributes.addFlashAttribute("ko", "Accès refusé.");
            return "redirect:/demande/fiche/" + id;
        }

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        // typeDemande = "OPEX" / "CAPEX" / "" (null)
        if (typeDemande == null || typeDemande.isBlank()) {
            demande.setNatureDemande(null);
        } else {
            demande.setNatureDemande(DemandeMere.NatureDemande.valueOf(typeDemande.trim().toUpperCase()));
        }

        demandeMereService.saveDemandeMere(demande);
        redirectAttributes.addFlashAttribute("ok", "Type de demande enregistré.");

        return "redirect:/demande/fiche/" + id;
    }

    @GetMapping("/fiche/{id}")
    public String demandeFiche(@PathVariable("id") String id,
                               Model model,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur utilisateur = utilisateurService.getUtilisateurByMail(principal.getMail());

        boolean isAdminOrMG = auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_MOYENS_GENERAUX")
        );

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable : " + id);
            return "redirect:/demande/list";
        }

        // accès : admin/MG tout, sinon moi + enfants
        if (!isAdminOrMG) {
            List<Integer> visibleIds = utilisateurService.getIdsUtilisateurVisible(utilisateur.getId());
            Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

            if (demandeurId == null || !visibleIds.contains(demandeurId)) {
                redirectAttributes.addFlashAttribute("ko", "Accès refusé à cette demande.");
                return "redirect:/demande/list";
            }
        }

        // ✅ canDecision : enfant (pas moi) uniquement
        List<Integer> childrenIds = utilisateurService.getIdsUtilisateurVisible(utilisateur.getId())
                .stream().filter(x -> !x.equals(utilisateur.getId())).toList();

        Map<Integer, String> statutLabels = new LinkedHashMap<>();
        statutLabels.put(StatutDemande.CREE, "CRÉÉE");
        statutLabels.put(StatutDemande.VALIDATION_N1, "EN VALIDATION");
        statutLabels.put(StatutDemande.VALIDATION_N2, "EN VALIDATION");
        statutLabels.put(StatutDemande.VALIDATION_N3, "EN VALIDATION");
        statutLabels.put(StatutDemande.VALIDE, "VALIDÉE");
        statutLabels.put(StatutDemande.REFUSE, "REFUSÉE");

        model.addAttribute("statutLabels", statutLabels);


        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;
        boolean canDecision = (demandeurId != null)
                && !demandeurId.equals(utilisateur.getId())
                && childrenIds.contains(demandeurId)
                && demande.getStatut() == StatutDemande.CREE;

        // lignes
        List<DemandeFille> lignes = demandeFilleService.getDemandeFilleByDemandeMere(demande);

        model.addAttribute("currentUri", request.getRequestURI());
        model.addAttribute("demande", demande);
        model.addAttribute("lignes", lignes);
        model.addAttribute("canDecision", canDecision);

        model.addAttribute("natures", DemandeMere.NatureDemande.values());

        return "demande/demande-fiche";
    }



    @PostMapping("/fiche/{id}/decision")
    public String decision(@PathVariable("id") String id,
                           @RequestParam("decision") String decision,
                           RedirectAttributes redirectAttributes, Model model) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        Utilisateur principal = (Utilisateur) auth.getPrincipal();
        Utilisateur current = utilisateurService.getUtilisateurByMail(principal.getMail());

        DemandeMere demande = demandeMereService.getDemandeMereById(id).orElse(null);
        if (demande == null) {
            redirectAttributes.addFlashAttribute("ko", "Demande introuvable.");
            return "redirect:/demande/list";
        }

        // enfants directs
        List<Integer> childrenIds = utilisateurService.getIdsUtilisateurVisible(current.getId())
                .stream().filter(x -> !x.equals(current.getId())).toList();

        Integer demandeurId = (demande.getDemandeur() != null) ? demande.getDemandeur().getId() : null;

        boolean canDecision = (demandeurId != null)
                && !demandeurId.equals(current.getId())
                && childrenIds.contains(demandeurId)
                && demande.getStatut() == StatutDemande.CREE;


        model.addAttribute("canDecision", canDecision);

        if (!canDecision) {
            // message plus clair selon le cas
            redirectAttributes.addFlashAttribute("ko", "Cette demande a déjà été traitée.");
            return "redirect:/demande/fiche/" + id;
        }

        String d = (decision == null) ? "" : decision.trim().toUpperCase();

        if ("APPROVE".equals(d)) {
            demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.VALIDATION_N1);
            redirectAttributes.addFlashAttribute("ok", "Demande envoyée en validation N1");
            return "redirect:/demande/fiche/" + id;
        }

        if ("REJECT".equals(d)) {
            demandeMereService.appliquerDecisionGlobale(demande, StatutDemande.REFUSE);
            redirectAttributes.addFlashAttribute("ok", "Demande rejetée");
            return "redirect:/demande/fiche/" + id;
        }

        redirectAttributes.addFlashAttribute("ko", "Décision invalide.");
        return "redirect:/demande/fiche/" + id;
    }

}
